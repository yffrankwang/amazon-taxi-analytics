from os import unlink
import boto3
import os, sys
import json
import datetime
import pandas as pd
import matplotlib.pyplot as plt


#bucket = sess.default_bucket()
bucket = "tlc-stack-artifactbucket-wwus3ytvirwq"
source = "kinesis-output/20211213/"
prefix = "sagemaker/nyctlc"
datafile = "source.csv"

# download files
s3c = boto3.client("s3")
s3r = boto3.resource("s3")


class ProgressPercentage(object):
	def __init__(self, total):
		self.total = total
		self.comsumed = 0
		self.percentage = 0

	def __call__(self, bytes_amount):
		self.comsumed += bytes_amount
		percentage = int(self.comsumed * 100 / self.total)
		if percentage > self.percentage:
			self.percentage = percentage
			sys.stdout.write(" %d%%" % percentage)
			sys.stdout.flush()
			
def download_files():
	if os.path.exists(datafile):
		print("%s exists, download skipped" % datafile)
		return

	with open(datafile, "ab") as fw:
		for o in s3r.Bucket(bucket).objects.filter(Prefix=source):
			sys.stdout.write("downloading " + o.key + " ... ")
			s3o = s3r.Object(bucket, o.key)
			s3o.download_fileobj(fw, Callback=ProgressPercentage(s3o.content_length))
			fw.flush()
			sys.stdout.write("\n")
			sys.stdout.flush()

download_files()

DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

def parse_datetime(ss):
	return [datetime.datetime.strptime(x, DATE_FORMAT) for x in ss]

def pd_read():
	print("-----LOAD---------------------------------------------")

	# read the input file, and display sample rows/columns
	pd.set_option("display.max_columns", 500)
	pd.set_option("display.max_rows", 50)
	df = pd.read_csv(datafile, header=None, 
		names=["TIMESTAMP", "GEOHASH", "PICKUP_COUNT", "LOCATION"],
		parse_dates=["TIMESTAMP"], date_parser=parse_datetime)

	# print first 10 lines to look at part of the dataset
	print(df[0:10])

	print("----SORT by TIMESTAMP, GEOHASH------------------------")
	df = df.sort_values(["TIMESTAMP", "GEOHASH"])
	print(df[0:10])

	return df

df = pd_read()

def make_unique_geohash():
	unique_geohash = df.GEOHASH.unique()
	number_of_geohash = len(unique_geohash)

	print("------------------------------------------------------")
	print("Unique GeoHash {}".format(number_of_geohash))
	print(
		"Minimum pickup date is {}, maximum pickup date is {}".format(
			df.TIMESTAMP.min(), df.TIMESTAMP.max()
		)
	)
	return unique_geohash

unique_geohash = make_unique_geohash()


def make_pickup_list(freq):
	pickup_list = []
	idx = pd.date_range(df.TIMESTAMP.min(), df.TIMESTAMP.max(), freq=freq)
	for key in unique_geohash:
		temp_df = df[["TIMESTAMP", "PICKUP_COUNT"]][df.GEOHASH == key]
		temp_df.set_index(["TIMESTAMP"], inplace=True)
		temp_df.index = pd.DatetimeIndex(temp_df.index)
		temp_df = temp_df.reindex(idx, fill_value=0)
		pickup_list.append(temp_df["PICKUP_COUNT"])

	# print first 10 items
	print("----PICKUP LIST---------------------------------------")
	print(pickup_list[0:10])
	return pickup_list

pickup_list = make_pickup_list("10min")


# plot
def plot():
	plt.figure(figsize=(12, 6), dpi=100, facecolor="w")
	for key, geohash in enumerate(unique_geohash):
		plt.plot(pickup_list[key], label=geohash)

	plt.ylabel("PickupCount")
	plt.xlabel("DateTime")
	plt.title("New York City TLC Pickup Count")
	plt.legend(loc="upper center", bbox_to_anchor=(0.5, -0.05), shadow=False, ncol=4)
	plt.show()

#plot()

# 1day = 10min * 6 * 24
prediction_length = 6 * 24

# Split the data for training and test
def make_train_list():
	training = []
	for i in pickup_list:
		training.append((i[:-prediction_length]))

	return training

pickup_list_training = make_train_list()

def format_datetime(d):
	return datetime.datetime.strftime(d, DATE_FORMAT)

def series_to_obj(ts, cat=None):
	obj = {
		"start": format_datetime(ts.index[0]), 
		"target": list(ts)
	}
	if cat:
		obj["cat"] = cat

	return obj


def series_to_jsonline(ts, cat=None):
	return json.dumps(series_to_obj(ts, cat))


encoding = "utf-8"
def save_train_data():
	file_name = "train.json"
	train_data_path = "{}/train/{}".format(prefix, file_name)

	print("------------------------------------------------------")
	print("saving %s" % file_name)
	with open(file_name, "wb") as fp:
		for ts in pickup_list_training:
			fp.write(series_to_jsonline(ts).encode(encoding))
			fp.write("\n".encode(encoding))

	print("uploading %s => s3://%s/%s" % (file_name, bucket, train_data_path))
	s3r.Object(bucket, train_data_path).upload_file(file_name)

def save_test_data():
	file_name = "test.json"
	test_data_path = "{}/test/{}".format(prefix, file_name)

	print("------------------------------------------------------")
	print("saving %s" % file_name)
	with open(file_name, "wb") as fp:
		for ts in pickup_list:
			fp.write(series_to_jsonline(ts).encode(encoding))
			fp.write("\n".encode(encoding))

	print("uploading %s => s3://%s/%s" % (file_name, bucket, test_data_path))
	s3r.Object(bucket, test_data_path).upload_file(file_name)

save_train_data()
save_test_data()
