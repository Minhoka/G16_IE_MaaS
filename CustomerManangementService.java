import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import java.sql.Date;

public class CustomerManangementService {

	public static void main(String[] args) {

		Properties props = new Properties();
		props.put("bootstrap.servers", "54.87.164.38:9092");//ec2 instance
		props.put("group.id", "Precision Products");//MessageGroup
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
		props.put("auto.commit.offset","false");
		//to commit manually 
		KafkaConsumer<String, String> consumer = new KafkaConsumer<String,String>(props);
		consumer.subscribe(Collections.singletonList("myTopic"));//TopicName
	
		long user_id = 0;
		Date checkin_ts = null;
		Date checkout_ts = null;
		double price = 0.0;
		double discount = 0.0;			
	
		Connection conn = null;
		boolean bd_ok = false;

		try 
		{
			Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection("jdbc:mysql://mytestdb.crjjgaudsykb.us-east-1.rds.amazonaws.com:3306/CustomerManangementService", "storemessages", "pedro1234");
			bd_ok = true;
		}
		catch (SQLException sqle)
		{
			System.out.println("SQLException: " + sqle);
		}
		catch (ClassNotFoundException e) 
		{
			e.printStackTrace();
		}
		try 
		{
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			

			
			DefaultHandler handler = new DefaultHandler() {

				long lUserId = user_id;
				Date sCheckIn = checkin_ts;
				Date sCheckOut = checkout_ts;
				double dDiscount = discount;

				public void startElement(String uri, String localName,String qName, Attributes attributes) throws SAXException 
				{
					if (qName.equalsIgnoreCase("USERID")) {
						lUserId = Long.parseLong(attributes.getValue(qName));
					}
					if (qName.equalsIgnoreCase("CHECKIN_TS")) {
						sCheckIn = Date.valueOf(attributes.getValue(qName));
					}
					if (qName.equalsIgnoreCase("CHECKOUT_TS")) {
						sCheckOut = Date.valueOf(attributes.getValue(qName));
					}
					if (qName.equalsIgnoreCase("hasStudentDiscount")) {
						dDiscount = Double.parseDouble(attributes.getValue(qName));
					}
				}

				public void characters(char ch[], int start, int length) throws SAXException {
					if (lUserId > 0) {
						user_id = lUserId;
					}

					if (sCheckIn != null) {
						checkin_ts = sCheckIn;
					}

					if (sCheckOut != null) {
						checkout_ts = sCheckOut;
					}

					if (dDiscount != 0) {
						discount = dDiscount;
					}
				}
			};
			
			while (true) 
			{
				ConsumerRecords<String, String> records = consumer.poll(100);
				for (ConsumerRecord<String, String> record : records) 
				{
					saxParser.parse(record.value(), handler);
					if(checkin_ts != null)
					{	
						System.out.printf("topic = %s, offset = %d,\n",record.topic(), record.offset());
						if (bd_ok) 
						{
							PreparedStatement s;
							s = conn.prepareStatement ("insert into AccountManager(topic, offset, user_id, checkin_ts, checkout_ts, price, discount) values(?,?,?,?,?,?,?)");
							s.setInt(1, 1/*TODO:FALTA METER AQUI O TOPIC ID, se calhar pode-se usar uma enumeracao */);
							s.setInt(2, new Long(record.offset()).intValue());
							s.setLong(3, user_id);
							s.setDate(4, checkin_ts);
							s.setDate(5, null);
							s.setDouble(6, 0);
							s.setDouble(7, 0);
							s.executeUpdate();
							s.close();
						}
					}
					if(checkout_ts != null)
					{	
						/*TODO:FAZER QUERY SELECT À BD para fazer os calculos de price e discount
						 * TODO: guardar o offset deste SELECT para usar a seguir no WHERE do update */
						
						System.out.printf("topic = %s, offset = %d,\n",record.topic(), record.offset());
						if (bd_ok) 
						{
							PreparedStatement s;
							s = conn.prepareStatement ("update AccountManager set (offset,checkout_ts, price, discount) values(?,?,?,?) where topic = ? and offset = ?");
							s.setInt(1, new Long(record.offset()).intValue());
							s.setDate(2, checkout_ts);
							s.setDouble(3, price);
							s.setDouble(4, discount);
							s.setInt(5, 1/*FALTA METER AQUI O TOPIC ID, se calhar pode-se usar uma enumeracao */);
							s.setInt(6, 1/*FALTA METER AQUI O offset anterior */);
							s.executeUpdate();
							s.close();
						}
					}
				}
				//Commit Current Offset 
				try 
				{
					consumer.commitSync();
				}
				catch (CommitFailedException e) 
				{
					System.out.printf("commit failed", e);
				}
			}
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		finally 
		{
			consumer.close();
			try 
			{
				conn.close();
			}
			catch (SQLException e) 
			{
				e.printStackTrace();
			}
		}
	}
}
