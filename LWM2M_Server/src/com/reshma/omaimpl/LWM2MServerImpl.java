package com.reshma.omaimpl;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.util.JSON;
import com.sun.jersey.api.client.ClientResponse.Status;

@Path("/omaserver")
public class LWM2MServerImpl {

	@Path("/bs")
	@POST
	@Produces("application/json")
	public Response getBootstrapInfo(@QueryParam("ep") String endPntClientNm) {
		JSONObject bootstrapResposeJSON = null;
		MongoClient mongoClient = null;
		try{
			System.out.println("End Point Client Name is : " + endPntClientNm); 
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("Bootstrap");
			Document document = new Document();
			document.put("ENDPNT_CLIENT_NM", endPntClientNm);
			//MongoCursor mongoCursor  = mongoCollection.find(document).sort(new Document("CREATE_DATE", -1)).limit(3).iterator();
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			String clientObj = "";
			String manufacturer = "";
			String serialNumber = "";
			String firwareVersion = "";
			String modelNo = "";
			while (mongoCursor.hasNext()) {
				JSON json =new JSON();
				/*Convert MongoDB document to JSON*/
		        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
				JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
				clientObj = dbClientDataJSON.getString("CLIENT_OBJECT").toString();
				manufacturer = dbClientDataJSON.getString("MANUFACTURER").toString();
				serialNumber = dbClientDataJSON.getString("SERIAL_NUMBER").toString();
				firwareVersion = dbClientDataJSON.getString("FIRMWARE_VERSION").toString();
				modelNo = dbClientDataJSON.getString("MODEL_NO").toString();
			}
			JSONObject bootstrapResponse = new JSONObject();
			bootstrapResponse.put("ENDPNT_CLIENT_NM", endPntClientNm);
			bootstrapResponse.put("CLIENT_OBJECT", clientObj);
			bootstrapResponse.put("MANUFACTURER", manufacturer);
			bootstrapResponse.put("SERIAL_NUMBER", serialNumber);
			bootstrapResponse.put("FIRMWARE_VERSION", firwareVersion);
			bootstrapResponse.put("MODEL_NO", modelNo);
			bootstrapResposeJSON = new JSONObject();
			bootstrapResposeJSON.put("BOOTSTRAPRESPONSE", bootstrapResponse);
			System.out.println("Bootstrap response JSON is : " + bootstrapResposeJSON.toString()); 
			mongoClient.close();
			return Response.status(Status.OK).entity(bootstrapResposeJSON.toString()).build();
		} catch (Exception e){
			mongoClient.close();
			e.printStackTrace();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Bootstrap request failed !!").build();
		}
	}
	
	
	@Path("/rd")
	@POST
	@Produces("application/json")
	public Response registerClient(@QueryParam("ep") String endPntClientNm, 
								   		@QueryParam("lifetime") String lifetime,
								   			@QueryParam("bindingMode") String bindingMode,
								   				@QueryParam("smsNumber") String smsNumber,
								   					@QueryParam("endUrl") String endUrl){
		MongoClient mongoClient = null;
		try {
			
			/*Obtain the registration request JSON mesaage from client and parse it*/
			System.out.println("End Point Client Name is : " + endPntClientNm); 
			
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("Registration");
			Document document = new Document();
			document.put("ENDPNT_CLIENT_NM", endPntClientNm);
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			
			/*Check if the registration record already exist for the End-Point-Client*/
			if(mongoCursor.hasNext()){
				/*If the registration record already exists, update it.*/
				while (mongoCursor.hasNext()) {
					/*Incrementing the cursor to prevent infinite while loop*/
					mongoCursor.next();
					System.out.println("Updating the registration record for the client - " + endPntClientNm); 
					Document updateDocument = new Document();
					updateDocument.append("LIFETIME", lifetime)
								.append("BINDING_MODE", bindingMode)
								.append("SMS_NBR", smsNumber)
								.append("ENDPOINT_ROBO_URL", endUrl)
								.append("CRTUPDT_DTTM", new Date());
					Document keyDocument = new Document().append("ENDPNT_CLIENT_NM", endPntClientNm);
					System.out.println("Update document - " + updateDocument); 
					mongoCollection.updateOne(keyDocument, new Document().append("$set", updateDocument));
					//Thread.sleep(2000);
				}
			} else {
				/*If the registration record doesn't exists, create it.*/
				System.out.println("Inserting the registration record for the client - " + endPntClientNm); 
				Document insertDocument = new Document();
				insertDocument.append("ENDPNT_CLIENT_NM", endPntClientNm)
							  .append("LIFETIME", lifetime)
							  .append("BINDING_MODE", bindingMode)
							  .append("SMS_NBR", smsNumber)
							  .append("ENDPOINT_ROBO_URL", endUrl)
							  .append("CRTUPDT_DTTM", new Date());;
				mongoCollection.insertOne(insertDocument);
			}
			mongoClient.close();
			return Response.status(Status.OK).entity("Registration request successfully completed !!").build();
		} catch (Exception e){
			e.printStackTrace();
			mongoClient.close();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Registration request failed !!").build();
		}
	}

	@Path("/NotifyRate/{EndPointClientName}")
	@POST
	@Produces("application/json")
	public Response updateProdRate(@PathParam("EndPointClientName") String endPntClientNm, String reqJson){
		MongoClient mongoClient = null;
		try {
			String rate = "";
			
			/*Obtain the registration request JSON message from client and parse it*/
			System.out.println("End Point Client Name is : " + endPntClientNm); 
			System.out.println("Rate update request message is : " + reqJson);   
			JSONObject rateJSON = new JSONObject(reqJson);
			JSONObject notifyRateRequest = rateJSON.getJSONObject("PRODRATEUPDATEREQUEST");
			rate = notifyRateRequest.getString("RATE").toString();
			
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("ProductionAssemblyRates");

			System.out.println("Inserting the notify rate message for the client - " + endPntClientNm); 
			Document insertDocument = new Document();
			insertDocument.append("ENDPNT_CLIENT_NM", endPntClientNm)
						  .append("RATE", rate)
						  .append("CRTUPDT_DTTM", new Date());
			mongoCollection.insertOne(insertDocument);
			
			
			mongoCollection = mongoDatabase.getCollection("ThresholdRates");
			
			Document document = new Document();
			document.put("ENDPNT_CLIENT_NM", endPntClientNm);
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			String thresholdRate = "";
			String roboType = "";
			String clientState = "";
			/*Check if the threshold rate exist for the End-Point-Client*/
			if(mongoCursor.hasNext()){
				while (mongoCursor.hasNext()) {
					JSON json =new JSON();
					/*Convert MongoDB document to JSON*/
			        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
			        JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
			        if (dbClientDataJSON.getString("ENDPNT_CLIENT_NM").toString().equals(endPntClientNm)){
			        	//thresholdRate = dbClientDataJSON("RATE").toString();
			        	//roboType = dbClientDataJSON.getString("RATE").toString();
			        	
				        thresholdRate = dbClientDataJSON.getString("THRESHOLD_RATE").toString();
				        roboType = dbClientDataJSON.getString("ROBO_TYPE").toString();			        	
			        }
				}
			} 		
						
			System.out.println("Threshold Rate/Notified Rate respectively for " + endPntClientNm + "is ---------- :" + Integer.parseInt(thresholdRate) + "/" + Integer.parseInt(rate));
			if (roboType.equals("MANUFACTURING") || roboType.equals("ASSEMBLY") || roboType.equals("PACKAGING")){
				if (Integer.parseInt(rate) >= Integer.parseInt(thresholdRate)){
					clientState = "NORMAL";
					System.out.println("Client state is calculated as NORMAL by LW M2M Server");
				} else {
					System.out.println("Client state is calculated as CRITICAL by LW M2M Server");
					clientState = "CRITICAL";
					mongoCollection = mongoDatabase.getCollection("Registration");
					document = new Document();
					mongoCursor  = mongoCollection.find().iterator();
					String clientURL = "";
					String clientEndPntNm = "";
					while (mongoCursor.hasNext()) {
						JSON json =new JSON();
						/*Convert MongoDB document to JSON*/
				        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
				        JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
				        clientEndPntNm = dbClientDataJSON.getString("ENDPNT_CLIENT_NM").toString();
				        clientURL = dbClientDataJSON.getString("ENDPOINT_ROBO_URL").toString();	
				        InputStream inputStream = null;
				        BufferedReader bufferedReader = null;
						String urlString = clientURL + "/Shutdown" ;
						URL url = new URL(urlString);
						HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
						httpURLConnection.setDoOutput(true);
						httpURLConnection.setDoInput(true);
						httpURLConnection.setRequestMethod("GET");
						inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
						bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
						StringBuilder stringBuilder = new StringBuilder();
						String line = null;
						while ((line =bufferedReader.readLine()) != null ){
							stringBuilder.append(line);
						}
						System.out.println("Shuttin down client ------- : " + endPntClientNm); 
						System.out.println("Response from client is ------- : " + stringBuilder.toString()); 
					}		
				}
			} else if (roboType.equals("QA")) {
				if (Integer.parseInt(rate) < Integer.parseInt(thresholdRate)){
					clientState = "NORMAL";
					System.out.println("Client state is calculated as NORMAL by LW M2M Server");
				} else {
					System.out.println("Client state is calculated as CRITICAL by LW M2M Server");
					mongoCollection = mongoDatabase.getCollection("Registration");
					document = new Document();
					mongoCursor  = mongoCollection.find().iterator();
					String clientURL = "";
					String clientEndPntNm = "";
					while (mongoCursor.hasNext()) {
						JSON json =new JSON();
						/*Convert MongoDB document to JSON*/
				        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
				        JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
				        clientEndPntNm = dbClientDataJSON.getString("ENDPNT_CLIENT_NM").toString();
				        clientURL = dbClientDataJSON.getString("ENDPOINT_ROBO_URL").toString();	
				        InputStream inputStream = null;
				        BufferedReader bufferedReader = null;
						String urlString = clientURL + "/Shutdown" ;
						URL url = new URL(urlString);
						HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
						httpURLConnection.setDoOutput(true);
						httpURLConnection.setDoInput(true);
						httpURLConnection.setRequestMethod("GET");
						inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
						bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
						StringBuilder stringBuilder = new StringBuilder();
						String line = null;
						while ((line =bufferedReader.readLine()) != null ){
							stringBuilder.append(line);
						}
						System.out.println("Shuttin down client ------- : " + endPntClientNm); 
						System.out.println("Response from client is -------: " + stringBuilder.toString()); 
					}		
				}
			}
			
			mongoClient.close();
			return Response.status(Status.OK).entity("Manufacturing rate update request successfully completed. Client status is " + clientState).build();
		} catch (Exception e){
			e.printStackTrace();
			mongoClient.close();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Registration request failed !!").build();
		}
	}
	
	@Path("/Deregistration/{EndPointClientName}")
	@DELETE
	@Produces("application/json")
	public Response deRegisterClient(@PathParam("EndPointClientName") String endPntClientNm) {
		MongoClient mongoClient = null;
		try {
			
			/*Obtain the registration request from client and process it*/
			System.out.println("End Point Client Name is : " + endPntClientNm); 
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("Registration");
			Document document = new Document();
			document.put("ENDPNT_CLIENT_NM", endPntClientNm);
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			
			/*Check if the registration record already exist for the End-Point-Client*/
			if(mongoCursor.hasNext()){
				mongoCollection.deleteOne(document);
			}	
			else {
				return Response.status(Status.BAD_REQUEST).entity("Client registration details not found !!").build();
			}
			mongoClient.close();
			return Response.status(Status.OK).entity("Client deregistration request successfully completed !!").build();
		} catch (Exception e){
			mongoClient.close();
			return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Client deregistration request failed !!").build();
		}
	}
	
	@Path("/GetClientStatus")	
	@GET
	@Produces("application/json")
	public Response getClientStatus() {
        MongoClient mongoClient = null; 
        StringBuilder stringBuilder = null;
        JSONObject indClientDataJSON = null;
        JSONArray tempJSONArray = new JSONArray();
        JSONObject clientDataJSON ;
        JSONObject clientDataParentJSON = new JSONObject();
		try {
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("Registration");
			Document document = new Document();
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			String clientURL = "";
			String clientEndPntNm = "";
			while (mongoCursor.hasNext()) {
				JSON json =new JSON();
				clientDataJSON = new JSONObject();
				/*Convert MongoDB document to JSON*/
		        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
		        JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
		        clientEndPntNm = dbClientDataJSON.getString("ENDPNT_CLIENT_NM").toString();
		        clientURL = dbClientDataJSON.getString("ENDPOINT_ROBO_URL").toString();	
		        InputStream inputStream = null;
		        BufferedReader bufferedReader = null;
				String urlString = clientURL + "/GetStatus" ;
				URL url = new URL(urlString);
				HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
				httpURLConnection.setDoOutput(true);
				httpURLConnection.setDoInput(true);
				httpURLConnection.setRequestMethod("GET");
				inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
				stringBuilder = new StringBuilder();
				String line = null;
				while ((line =bufferedReader.readLine()) != null ){
					stringBuilder.append(line);
				}  
				System.out.println("Status Response from client - " + clientEndPntNm + " is " + stringBuilder.toString());
				indClientDataJSON = new JSONObject(stringBuilder.toString());
				clientDataJSON.put("ROBOT_ID", clientEndPntNm);
			    clientDataJSON.put("STATUS", indClientDataJSON.getJSONObject("STATUSRESPONSE").getString("STATUS").toString());
			    clientDataJSON.put("CURRENT_RATE", indClientDataJSON.getJSONObject("STATUSRESPONSE").getString("CURRENT_RATE").toString());
			    tempJSONArray.put(clientDataJSON);
			}
			clientDataParentJSON.put("CLIENTSTATUSRESPONSE", tempJSONArray);
		    } catch (Exception e) {
		       e.printStackTrace();
		       return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Get Client Status request failed !!").build();
		    } 
        return Response.status(Status.OK).entity(clientDataParentJSON.toString()).build();
	}	
	
	@Path("/RestartProductionLine")	
	@GET
	@Produces("application/json")
	public Response startProductionLine() {
        MongoClient mongoClient = null; 
        StringBuilder stringBuilder = null;
		try {
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("Registration");
			Document document = new Document();
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			String clientURL = "";
			String clientEndPntNm = "";
			while (mongoCursor.hasNext()) {
				JSON json =new JSON();
				/*Convert MongoDB document to JSON*/
		        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
		        JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
		        clientEndPntNm = dbClientDataJSON.getString("ENDPNT_CLIENT_NM").toString();
		        clientURL = dbClientDataJSON.getString("ENDPOINT_ROBO_URL").toString();	
		        InputStream inputStream = null;
		        BufferedReader bufferedReader = null;
				String urlString = clientURL + "/StartProductionLine" ;
				URL url = new URL(urlString);
				HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
				httpURLConnection.setDoOutput(true);
				httpURLConnection.setDoInput(true);
				httpURLConnection.setRequestMethod("POST");
				inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
				stringBuilder = new StringBuilder();
				String line = null;
				while ((line =bufferedReader.readLine()) != null ){
					stringBuilder.append(line);
				}  
				System.out.println("Status Response from client - " + clientEndPntNm + " is - " + stringBuilder.toString());
			}
		    } catch (Exception e) {
		       e.printStackTrace();
		       return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Production Line Re-Start request failed !!").build();
		    } 
        return Response.status(Status.OK).entity("Production Line Re-Start request is successfull !!").build();
	}		
	
	@Path("/StopProductionLine")	
	@GET
	@Produces("application/json")
	public Response stopProductionLine() {
        MongoClient mongoClient = null; 
        StringBuilder stringBuilder = null;
		try {
			mongoClient = new MongoClient("localhost" , 27017 );
			MongoDatabase mongoDatabase = mongoClient.getDatabase("TestMongoDB");
			MongoCollection mongoCollection = mongoDatabase.getCollection("Registration");
			Document document = new Document();
			MongoCursor mongoCursor  = mongoCollection.find(document).iterator();
			String clientURL = "";
			String clientEndPntNm = "";
			while (mongoCursor.hasNext()) {
				JSON json =new JSON();
				/*Convert MongoDB document to JSON*/
		        String dbClientDataJSONStr = json.serialize(mongoCursor.next());
		        JSONObject dbClientDataJSON = new JSONObject(dbClientDataJSONStr);
		        clientEndPntNm = dbClientDataJSON.getString("ENDPNT_CLIENT_NM").toString();
		        clientURL = dbClientDataJSON.getString("ENDPOINT_ROBO_URL").toString();	
		        InputStream inputStream = null;
		        BufferedReader bufferedReader = null;
				String urlString = clientURL + "/Shutdown" ;
				URL url = new URL(urlString);
				HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
				httpURLConnection.setDoOutput(true);
				httpURLConnection.setDoInput(true);
				httpURLConnection.setRequestMethod("GET");
				inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
				stringBuilder = new StringBuilder();
				String line = null;
				while ((line =bufferedReader.readLine()) != null ){
					stringBuilder.append(line);
				}
				System.out.println("Shuttin down client ------- : " + clientEndPntNm); 
				System.out.println("Response from client is -------: " + stringBuilder.toString()); 
			}	
	    } catch (Exception e) {
	       e.printStackTrace();
	       return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Stop Production Line request failed !!").build();
	    } 
        return Response.status(Status.OK).entity("Stop Production Line request is successfull !!").build();
	}		
}
