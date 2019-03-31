DROP DATABASE IF EXISTS collectedMSGs; 
CREATE DATABASE IF NOT EXISTS collectedMSGs;
USE collectedMSGs; 
DROP TABLE IF EXISTS Message; 
CREATE TABLE Message( 
offset INTEGER PRIMARY KEY, 
groupId VARCHAR(100) NOT NULL, 
contentKey VARCHAR(100), 
contentValue VARCHAR(1000));

select * from Message;


CREATE DATABASE IF NOT EXISTS CustomerManangementService;
use CustomerManangementService;
-- drop table AccountManager;
CREATE TABLE AccountManager (
topic int,
offset bigint,
user_id int,
checkin_ts timestamp,
checkout_ts timestamp,
price float,
discount float,
primary key (topic, offset)
);
select * from AccountManager;

CREATE DATABASE IF NOT EXISTS ServiceOfRevenueDistribution;
use ServiceOfRevenueDistribution;
-- drop table Settlement;
CREATE TABLE Settlement(
topic int,
day date,
offset bigint,
total_price float,
total_discount float,
revenue float,
primary key (topic, day)
);
select * from Settlement;

