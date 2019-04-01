import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Properties;
import javax.xml.parsers.*;
import java.io.*;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class CustomerManagementService {

	public static String[] parseMessage(String message) {
		
		String[] messageParsed = new String[4];	// Isto é o que vai ser devolvido neste método. É o que "importa" na mensagem
	
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			ByteArrayInputStream input = new ByteArrayInputStream(message.getBytes("UTF-8"));
			Document doc = builder.parse(input);
			
			Element root = doc.getDocumentElement();
			
			messageParsed[0] = root.getNodeName();	// checkIn || checkOut
			messageParsed[1] = root.getElementsByTagName("userId").item(0).getTextContent(); 	// Id do user
			messageParsed[2] = root.getElementsByTagName("timestamp").item(0).getTextContent();	// timestamp

			if (root.getChildNodes().getLength() == 3 )	// Significa que é um checkOut
				messageParsed[3] = root.getElementsByTagName("hasStudentDiscount").item(0).getTextContent();
			
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}

		return messageParsed;
	}
	
	public static float calculatePriceOfTrip (Timestamp checkIn, Timestamp checkOut) {
		//TODO Calcular o preço da viagem com base nos timestamps
		
		return 0;
	}

	public static void main(String[] args) {
		
		Properties props = new Properties();
		props.put("bootstrap.servers", "54.175.92.214:9092"); // IP da instância AWS
		props.put("group.id", "MaaS");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("auto.commit.offset", "false"); // to commit manually
		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
		consumer.subscribe(Collections.singletonList("test")); // "test" é o tópico
		Connection conn = null;
		boolean bd_ok = false;
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection(
					"jdbc:mysql://mytestdb.cdi8jvvabsus.us-east-1.rds.amazonaws.com:3306/CustomerManangementService",
					"storemessages", "storemessages"); // ("jdbc:mysql://yourAWSDBIP:3306/YOURDATABASENAME","YOURMasterUSERNAME","YOURPASSWORD")												
			bd_ok = true;
		} catch (SQLException sqle) {
			System.out.println("SQLException: " + sqle);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		try {
			while (true) {
				ConsumerRecords<String, String> records = consumer.poll(100);
				
				for (ConsumerRecord<String, String> record : records) {				
					String message = record.value();	//mensagem em formato XML
					String topic = record.topic();	//tópico onde a mensagem foi publicada
					long offset = record.offset();
					
					String[] messageParsed = parseMessage(message);	//Partir a mensagem XML em strings
					/*
					messageParsed[0] --> "checkIn" || "checkOut"
					messageParsed[1] --> userId
					messageParsed[2] --> timestamp
					messageParsed[3] --> se for checkIn está a null. Se for checkOut true || false
					*/
					
					int userId = Integer.parseInt(messageParsed[1]);
					
					if (bd_ok) {
						PreparedStatement s;
						
						if (messageParsed[0].equals("checkIn")) {
							s = conn.prepareStatement("INSERT INTO AccountManager VALUES(?,?,?,?,?,?,?)");
							s.setString(1, topic);	// topic
							s.setLong(2, offset);	// offset
							s.setInt(3, userId);	// user_id
							s.setTimestamp(4, java.sql.Timestamp.valueOf(messageParsed[2]));	// checkin_ts
							s.setTimestamp(5, null);	// checkout_ts
							s.setString(6, null);	// price
							s.setString(7, null);	// discount
							s.executeUpdate();
						} else {	// checkOut --> update na entrada do último checkIn (completar a informação que falta)
							
							//Começamos por ir buscar o offset do último checkIn do user no transporte
							long maxOffset = -1;	// -1 não quer dizer nada; só para inicializar a variável
							
							s = conn.prepareStatement("SELECT MAX(offset) " + 
													  "FROM AccountManager " + 
													  "WHERE topic=\"" + topic + "\" AND user_id=" + userId
													  );
							ResultSet maxOffsetFromQuery = s.executeQuery();

							while (maxOffsetFromQuery.next()) 
								maxOffset = maxOffsetFromQuery.getLong("MAX(offset)");	//Offset máximo de um dado user num dado tópico. 
																						//Equivale ao offset do seu último checkIn
							
							//Ir buscar o timestamp do checkIn para poder calcular o preço a pagar
							Timestamp checkInTimestamp = null;
							
							s = conn.prepareStatement("SELECT checkin_ts " + 
									  				  "FROM AccountManager " + 
									  				  "WHERE topic=\"" + topic + "\" AND user_id=" + userId + " AND offset=" + maxOffset
									  				 );
							ResultSet checkInTimestampFromQuery = s.executeQuery();
							
							while (checkInTimestampFromQuery.next()) 
								checkInTimestamp = checkInTimestampFromQuery.getTimestamp("checkin_ts");

							Timestamp checkOutTimestamp = java.sql.Timestamp.valueOf(messageParsed[2]);	// O timestamp do checkout vem no evento
							
							float price = calculatePriceOfTrip(checkInTimestamp, checkOutTimestamp); 
							
						    float discount = 0;
						    
						    if (messageParsed[3].equals("true"))	//Se houver desconto, é metade do preço
						    	discount = price / 2;
							
							s = conn.prepareStatement("UPDATE AccountManager " + 
													  "SET offset= ?, checkout_ts=?, price=?, discount=? " + 
													  "WHERE topic=? AND user_id=? AND offset=?"
													 );
							s.setLong(1, offset);
							s.setTimestamp(2, checkOutTimestamp);
							s.setFloat(3, price);
							s.setFloat(4, discount);
							s.setString(5, topic);
							s.setInt(6, userId);
							s.setLong(7, maxOffset);
							s.executeUpdate();
						}
						s.close();
					}	
				}
				try {	//Commit Current Offset
					consumer.commitSync();
				} catch (CommitFailedException e) {
					System.out.printf("commit failed", e);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			consumer.close();
			try {
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}
