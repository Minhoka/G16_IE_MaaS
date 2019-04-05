package services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;
import javax.xml.parsers.*;
import java.io.*;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

public class ServiceOfRevenueDistribution {
	
	public static String[] parseMessage(String message) {
		
		String[] messageParsed = new String[2];	// Isto e o que vai ser devolvido neste método. É o que "importa" na mensagem
	
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			ByteArrayInputStream input = new ByteArrayInputStream(message.getBytes("UTF-8"));
			Document doc = builder.parse(input);
			
			Element root = doc.getDocumentElement();
			
			//Aqui nao importa termos o elemento "parent" do XML
			messageParsed[0] = root.getElementsByTagName("value").item(0).getTextContent();	// value
			messageParsed[1] = root.getElementsByTagName("date").item(0).getTextContent();	// date
			
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}

		return messageParsed;
	}

	public static void main(String[] args) {

		Properties props = new Properties();
		props.put("bootstrap.servers", "100.26.43.176:9092"); // IP da instancia AWS
		props.put("group.id", "MaaS");
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		//props.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
		//props.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");
		props.put("auto.commit.offset", "false"); // to commit manually
		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
		
		// Meter o subscriber a subscrever os topicos que queremos
		ArrayList<String> listOfTopics = new ArrayList<String> ();
		listOfTopics.add("RevenueMetro");
		listOfTopics.add("RevenueTrain");
		listOfTopics.add("RevenueUber");
		consumer.subscribe(listOfTopics);
		
		Connection conn = null;
		boolean bd_ok = false;
		
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			conn = DriverManager.getConnection(
					"jdbc:mysql://microservices.cdi8jvvabsus.us-east-1.rds.amazonaws.com:3306/ServiceOfRevenueDistribution",
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
					
					String[] messageParsed = parseMessage(message);	//Partir a mensagem XML em strings
					//messageParsed[0] --> value
					//messageParsed[1] --> date
					
					float tripRevenue = Float.parseFloat(messageParsed[0]);
					
					if (bd_ok) {
						PreparedStatement s;
						
						System.out.println(" ******* Start Consuming clientRevenue ******* \n");
						
						//Se este dia ainda nao teve qualquer registo, inicializar uma linha na tabela para ele
						
						//Verificar se ja ha uma linha deste dia e deste topico
						//Exemplo >> SELECT * FROM Settlement WHERE topic="RevenueMetro" AND day="2019-04-02";
						System.out.println("SELECT * FROM Settlement"+ 
								" WHERE topic=\"" + topic + "\" AND day=\"" + messageParsed[1] + "\"");
						
						s = conn.prepareStatement("SELECT * " + 
								  "FROM Settlement " + 
								  "WHERE topic=\"" + topic + "\" AND day=\"" +  messageParsed[1] + "\""
								  );
						ResultSet topicAndDay = s.executeQuery();
						
						Boolean topicAndDayExists = topicAndDay.next();	
						//true --> Ja existe esse topico nesse dia
						//false --> Nao existe, temos de o criar!
						
						if (!topicAndDayExists) { // Criar o registo desse topico/dia
							
							//Exemplo >> INSERT INTO Settlement VALUES("RevenueMetro",3,0,"2019-04-02");
							System.out.println("INSERT INTO Settlement"+ 
									" VALUES (" + topic + ", " + offset + ", " + "0," + messageParsed[1] + ") ");
							
							s = conn.prepareStatement("INSERT INTO Settlement VALUES(?,?,?,?)");
							
							s.setString(1, topic);	// topic
							s.setLong(2, offset);	// offset
							s.setFloat(3, 0);	// revenue, comeca a 0 para cada novo dia 
							s.setString(4, messageParsed[1]);	// day
							s.executeUpdate();
						}
																
						//Exemplo >> UPDATE Settlement SET offset=2, revenue=revenue+10 WHERE topic="RevenueMetro" AND day="2019-04-02";
						System.out.println("UPDATE Settlement SET offset=" + offset + ", revenue=revenue+" + tripRevenue + 
											" WHERE topic=\"" + topic + "\" AND day=\"" + messageParsed[1] + "\"\n");
						
						s = conn.prepareStatement("UPDATE Settlement " + 
												  "SET offset= ?, revenue=revenue+? " + 
												  "WHERE topic=? AND day=?"
												 );
						s.setLong(1, offset);
						s.setFloat(2, tripRevenue);
						s.setString(3, topic);
						s.setString(4, messageParsed[1]);
						s.executeUpdate();
						
						System.out.println(" +++++ Finished Consuming clientRevenue +++++ \n");
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
