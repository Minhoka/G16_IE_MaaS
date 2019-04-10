package services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import javax.xml.parsers.*;
import java.io.*;
import java.math.BigDecimal;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class CustomerManagementService {

	public static String[] parseMessage(String message, String topic) {
		
		String[] messageParsed = new String[4];	// Isto e o que vai ser devolvido neste metodo. E o que "importa" na mensagem
	
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			ByteArrayInputStream input = new ByteArrayInputStream(message.getBytes("UTF-8"));
			Document doc = builder.parse(input);
			
			Element root = doc.getDocumentElement();
			
			messageParsed[0] = root.getNodeName();	// checkIn || checkOut
			messageParsed[1] = root.getElementsByTagName("userId").item(0).getTextContent(); 	// Id do user
			messageParsed[2] = root.getElementsByTagName("timestamp").item(0).getTextContent();	// timestamp
			
			if (messageParsed[0].equals("checkOut")) {	
				if (!topic.equals("MonitorUber")) {	
					messageParsed[3] = root.getElementsByTagName("hasStudentDiscount").item(0).getTextContent();
				} else {
					messageParsed[3] = root.getElementsByTagName("operatorPrice").item(0).getTextContent();
				}
			}
			
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}

		return messageParsed;
	}
	
	public static float calculatePriceOfTrip (Timestamp checkIn, Timestamp checkOut) {
		
		long in = checkIn.getTime();
		long out = checkOut.getTime();
		float price = (float)(out - in) / 1000000;
		
		return price;
	}
	
	public static float myRound (double dDouble) { // Arredonda o valor para 2 casas decimais
		
		BigDecimal bd = new BigDecimal(dDouble);
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
		
		return bd.floatValue();
	}
	
	public static String timestampToDate (Timestamp timestamp) {
		
		Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp.getTime());
        Date date = calendar.getTime();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		
        return sdf.format(date);
	}
	
	public static String getRevenueTopic (String topic) {
		
		switch(topic) {	// Mandar a mensagem para o topico do operador correto
			case "MonitorMetro":
				return "RevenueMetro";
			case "MonitorTrain":
				return "RevenueTrain";
			case "MonitorUber":
				return "RevenueUber";
			default:
				System.out.println("I don't know to which topic I should send the message to!");
				return "";
		}
	}

	public static void main(String[] args) {
		
		Properties props = new Properties();
		props.put("bootstrap.servers", "3.93.68.133:9092"); // IP da instancia AWS
		props.put("group.id", "MaaS");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");
		props.put("auto.commit.offset", "false"); // to commit manually
		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
		KafkaProducer<String, String> producer = new KafkaProducer<String, String>(props);
		
		// Meter o subscriber a subscrever os topicos que queremos
		ArrayList<String> listOfTopics = new ArrayList<String> ();
		listOfTopics.add("MonitorMetro");
		listOfTopics.add("MonitorTrain");
		listOfTopics.add("MonitorUber");
		consumer.subscribe(listOfTopics);
		
		Connection conn = null;
		boolean bd_ok = false;
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection(
					"jdbc:mysql://microservices.cdi8jvvabsus.us-east-1.rds.amazonaws.com:3306/CustomerManagementService",
					"microservices", "microservices"); // ("jdbc:mysql://yourAWSDBIP:3306/YOURDATABASENAME","YOURMasterUSERNAME","YOURPASSWORD")												
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
					String topic = record.topic();	//topico onde a mensagem foi publicada
					long offset = record.offset();
					
					String[] messageParsed = parseMessage(message, topic);	//Partir a mensagem XML em strings
					/*
					messageParsed[0] --> "checkIn" || "checkOut"
					messageParsed[1] --> userId
					messageParsed[2] --> timestamp
					messageParsed[3] --> se for checkIn esta a null. Se for checkOut true || false. Se for Uber e o valor
					*/
					
					if (bd_ok) {
						PreparedStatement s = null; //so uma inicializacao

						int userId = Integer.parseInt(messageParsed[1]);
 						
						switch(messageParsed[0]) {
							case "checkIn":
								System.out.println(" ******* Start Consuming checkIn ******* \n");
								
								System.out.println(" INSERT INTO AccountManager "
										+ "VALUES(" + topic + ", " + offset + ", " + userId + ", " + messageParsed[2] + ", null, null, null) \n");
								
								s = conn.prepareStatement("INSERT INTO AccountManager VALUES(?,?,?,?,?,?,?)");
								
								s.setString(1, topic);	// topic
								s.setLong(2, offset);	// offset
								s.setInt(3, userId);	// user_id
								s.setTimestamp(4, java.sql.Timestamp.valueOf(messageParsed[2]));	// checkin_ts
								s.setTimestamp(5, null);	// checkout_ts
								s.setString(6, null);	// price
								s.setString(7, null);	// discount
								s.executeUpdate();
								
								System.out.println(" +++++ Finished Consuming checkIn +++++ \n ");
								break;
								
							case "checkOut":
								
								System.out.println(" ******* Start Consuming checkOut ******* \n");
								// checkOut --> update na entrada do ultimo checkIn (completar a informacao que falta)
								
								//Comecamos por ir buscar o offset do ultimo checkIn do user no transporte
								long maxOffset = -1;	// -1 nao quer dizer nada; so para inicializar a variavel
								
								s = conn.prepareStatement("SELECT MAX(offset) " + 
														  "FROM AccountManager " + 
														  "WHERE topic=\"" + topic + "\" AND user_id=" + userId
														  );
								ResultSet maxOffsetFromQuery = s.executeQuery();
	
								while (maxOffsetFromQuery.next()) 
									maxOffset = maxOffsetFromQuery.getLong("MAX(offset)");	//Offset maximo de um dado user num dado topico. 
																							//Equivale ao offset do seu ultimo checkIn
								System.out.println("maxOffset: " + maxOffset);
								//Ir buscar o timestamp do checkIn para poder calcular o preco a pagar
								Timestamp checkInTimestamp = null;
								
								s = conn.prepareStatement("SELECT checkin_ts " + 
										  				  "FROM AccountManager " + 
										  				  "WHERE topic=\"" + topic + "\" AND user_id=" + userId + " AND offset=" + maxOffset
										  				 );
								ResultSet checkInTimestampFromQuery = s.executeQuery();
								
								while (checkInTimestampFromQuery.next()) 
									checkInTimestamp = checkInTimestampFromQuery.getTimestamp("checkin_ts");
								
								Timestamp checkOutTimestamp = java.sql.Timestamp.valueOf(messageParsed[2]);	// O timestamp do checkout vem no evento
								
								System.out.println("checkInTimestamp: " + checkInTimestamp + " | checkOutTimestamp: " + checkOutTimestamp);
								
								float price = 0;
								float discount = 0;
								if (!topic.equals("MonitorUber")) { 
									price = calculatePriceOfTrip(checkInTimestamp, checkOutTimestamp); 
								
									if (messageParsed[3].equals("true"))	//Se houver desconto, fica metade do preco
										discount = price / 2;
		
									price = myRound(price);
									discount = myRound(discount);
								} else {
									price = Float.parseFloat(messageParsed[3]);
								}
								System.out.println("UPDATE AccountManager " + 
										  "SET offset= " + offset + ", checkout_ts= " + checkOutTimestamp + ", price= " + price + ", discount= " + discount + 
										  " WHERE topic= " + topic + " AND user_id= " + userId + " AND offset= " + maxOffset + "\n");
								
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
								
								System.out.println(" +++++ Finished Consuming checkOut +++++ \n");
								
								System.out.println(" ----- Producing paymentInfo ----- \n");

						        String checkOutDate = timestampToDate(checkOutTimestamp);	//Devolve uma data no formato "2017-06-15"
						        
								float revenue = price - discount;
								revenue = myRound(revenue);
								
								String revenueOperatorTopic = getRevenueTopic(topic);
								
								System.out.println("Sending message: "
										+ "<paymentInfo><price>" + price + "</price><discount>" + discount + "</discount><revenue>" + revenue + "</revenue><date>" + checkOutDate + "</date></paymentInfo>\n"
										+ "To topic: " + revenueOperatorTopic);

								ProducerRecord<String, String> revenueMessage = new ProducerRecord<>(revenueOperatorTopic, "MaaS", "<paymentInfo><price>" + price + "</price><discount>" + discount + "</discount><revenue>" + revenue + "</revenue><date>" + checkOutDate + "</date></paymentInfo>");
								
								try{ 
									producer.send(revenueMessage);	// Fire-and-forget
								} 
								catch (Exception e){ 
									e.printStackTrace();
								} 
								break;
								
								default:
									System.out.println("The event received is neither a checkIn nor a checkOut!");
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