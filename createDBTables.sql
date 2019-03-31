BD: CustomerManangementService

DROP DATABASE IF EXISTS CustomerManangementService;
CREATE DATABASE IF NOT EXISTS CustomerManangementService;

use CustomerManangementService;

CREATE TABLE AccountManager(
topic INT NOT NULL,
offset BIGINT NOT NULL,
user_id INT NOT NULL,
checkin_ts DATETIME NOT NULL,
checkout_ts DATETIME,
price FLOAT,
discount FLOAT,
PRIMARY KEY (topic, offset)
);



BD: ServiceOfRevenueDistribution;

DROP DATABASE IF EXISTS ServiceOfRevenueDistribution;
CREATE DATABASE IF NOT EXISTS ServiceOfRevenueDistribution;

use ServiceOfRevenueDistribution;

CREATE TABLE Settlement(
topic INT NOT NULL,
offset BIGINT NOT NULL,
revenue FLOAT NOT NULL,
day DATE NOT NULL,
PRIMARY KEY (topic, day)
);