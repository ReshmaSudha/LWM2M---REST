package com.reshma.omaimpl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import javax.ws.rs.GET;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import com.sun.jersey.api.client.ClientResponse.Status;

@Path("/omaclient")
public class LWM2MClientImpl {
	/*Client initiated bootstrap implementation. 
	 * End-Point-Client-Name is hard coded in URL to make it unique.*/
	@Path("/Bootstrap/{EndPointClientName}")	
	@GET
	@Produces("application/json")
	public Response performClientInitBootstrap(@PathParam("EndPointClientName") String endPntCltNm) {
		Connection mysqlDBConn = null;
        Statement statement = null;
        ResultSet resultSet = null;
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        try {
        	String dbName = "mysqldb_client2";
			String clientObj = "";
			String manufacturer = "";
			String serialNumber = "";
			String firwareVersion = "";
			String modelNo = "";

			/*Send request Bootstrap server for bootstrapping*/
			String urlString = "http://localhost:8080/LWM2M_Server/omaserver/bs?ep=" + endPntCltNm;
			URL url = new URL(urlString);
			HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setDoInput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("POST");
			inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder stringBuilder = new StringBuilder();
			String line = null;
			while ((line =bufferedReader.readLine()) != null ){
				stringBuilder.append(line);
			}
			/*Obtain the bootstrap response from Bootstrap server and parse the message*/
			JSONObject responseJSON = new JSONObject(stringBuilder.toString());
			System.out.println("Response JSON is : " + responseJSON.toString());   
			JSONObject bootstrapResponse = responseJSON.getJSONObject("BOOTSTRAPRESPONSE");
			//JSONObject bootstrapResponse = new JSONObject(stringBuilder.toString());
			//System.out.println("Response JSON is : " + bootstrapResponse.toString());  			
			clientObj = bootstrapResponse.getString("CLIENT_OBJECT").toString();
			manufacturer = bootstrapResponse.getString("MANUFACTURER").toString();
			serialNumber = bootstrapResponse.getString("SERIAL_NUMBER").toString();
			firwareVersion = bootstrapResponse.getString("FIRMWARE_VERSION").toString();
			modelNo = bootstrapResponse.getString("MODEL_NO").toString();
			/*Insert or Update the bootstrap information into the client MySQL Database*/
			Class.forName("com.mysql.jdbc.Driver");
	        String mySQLJDBCUrl = "jdbc:mysql://localhost/" + dbName;
	        String userID = "mysqladmin";
	        String password = "mysqladmin";			
        	mysqlDBConn = DriverManager.getConnection(mySQLJDBCUrl, userID, password);
        	statement = mysqlDBConn.createStatement();
        	/*Check if a bootstrap entry is already present in the MySQL table.
        	 * This is prevent duplicate insert scenario*/
        	String selectStmt = "SELECT "
			        			+ "clt_init_btsp_dets.ENDPNT_CLIENT_NM "
			        			+ "FROM "
			        			+ dbName +".clt_init_btsp_dets "
			        			+ "WHERE ENDPNT_CLIENT_NM = '" 
			        			+ endPntCltNm 
			        			+ "'";
        	resultSet = statement.executeQuery(selectStmt);         
        	if (resultSet.next()) {
        		/*If an entry is already present (i.e. if resultSet is not empty), 
        		 * update the row with newly obtained details from bootstrap server*/
        		System.out.println("Updating the existing bootstrap record in the database...");  
            	String updateStmt = "UPDATE " 
            						+ dbName +".clt_init_btsp_dets"
			            			+ " SET"
			            			+ " CLIENT_OBJECT = '" + clientObj
			            			+ "' , MANUFACTURER = '" + manufacturer
			            			+ "' , SERIAL_NUMBER = '" + serialNumber
			            			+ "' , FIRMWARE_VERSION = '" + firwareVersion
			            			+ "' , MODEL_NO = '" + modelNo			            			
			            			+ "' , CRT_UPDT_DTTM = SYSDATE()"	
			            			+ " WHERE ENDPNT_CLIENT_NM = '" + endPntCltNm 
			            			+ "'";
            	int updateCnt = statement.executeUpdate(updateStmt); 
            	System.out.println("SQL statement is : " + updateStmt);
            } else {
            	/*If an entry is not present (i.e. if resultSet is empty), insert the new details*/
            	System.out.println("Inserting a new bootstrap record into database..."); 
            	String insertStmt = "INSERT INTO " 
            						+ dbName + ".clt_init_btsp_dets"
            						+ " (ENDPNT_CLIENT_NM, CLIENT_OBJECT, MANUFACTURER, SERIAL_NUMBER, FIRMWARE_VERSION, MODEL_NO, CRT_UPDT_DTTM) VALUES ('"
            						+ endPntCltNm
            						+ "','" + clientObj
            						+ "','" + manufacturer
            						+ "','"+ serialNumber
            						+ "','" + firwareVersion
            						+ "','"+ modelNo
            						+ "', SYSDATE())";
            	int InsertCnt = statement.executeUpdate(insertStmt); 
            	System.out.println("SQL statement is : " + insertStmt);
            }
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Client-initiated bootstrap request failed !!").build();
        } finally {
            try {
                if (resultSet != null) { resultSet.close(); }
                if (statement != null) { statement.close(); }
                if (mysqlDBConn != null) { mysqlDBConn.close(); }
        		if (inputStream != null) {inputStream.close();}
        		if (bufferedReader != null) {bufferedReader.close();}
            } catch (Exception e) {
            	e.printStackTrace();
            	return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Client-initiated bootstrap request failed !!").build();
            }
        }
        return Response.status(Status.OK).entity("Client-initiated bootstrap request successfull !!").build();
	}
	
	
	@Path("/Registration/{EndPointClientName}")
	@POST
	@Produces("application/json")
	public Response sendRegistrationReq(@PathParam("EndPointClientName") String endPntCltNm, String registrationReqStr) {
        OutputStreamWriter outputStreamWriter = null;
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
               
        try {
        	System.out.println("Registration request JSON from client UI is :" + registrationReqStr);
    		JSONObject registrationJSON = new JSONObject(registrationReqStr);
    		JSONObject registrationReqStrJSON = registrationJSON.getJSONObject("REGISTRATIONREQUEST");
    		String lifetime = registrationReqStrJSON.getString("LIFETIME").toString();
    		String bindingMode = registrationReqStrJSON.getString("BINDING_MODE").toString();
    		String smsNumber = registrationReqStrJSON.getString("SMS_NUMBER").toString();
    		String endUrl = registrationReqStrJSON.getString("ENDPOINT_ROBO_URL").toString();        	
        	
			/*Send registration request to the server*/
			String urlString = "http://localhost:8080/LWM2M_Server/omaserver/rd?ep=" + endPntCltNm
								+ "&lifetime=" + lifetime + "&bindingMode=" +  bindingMode
								+ "&smsNumber=" + smsNumber + "&endUrl=" + endUrl;
			URL url = new URL(urlString);
			HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setDoInput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("POST");
			outputStreamWriter = new OutputStreamWriter(httpURLConnection.getOutputStream());
			System.out.println("Registration request from client - "+ endPntCltNm + " is : " + registrationReqStr); 
			outputStreamWriter.write(registrationReqStr);
			outputStreamWriter.flush();

			inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder stringBuilder = new StringBuilder();
			String line = null;
			while ((line =bufferedReader.readLine()) != null ){
				stringBuilder.append(line);
			}
			/*Obtain the registration response from server and parse the message*/
			System.out.println("Response from server is : " + stringBuilder.toString());   
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Registration request failed !!").build();
        } finally {
        	try {
        		if (outputStreamWriter != null) {outputStreamWriter.close();}
        		if (inputStream != null) {inputStream.close();}
        		if (bufferedReader != null) {bufferedReader.close();}
        	}
        	catch (Exception e){
        		e.printStackTrace();
        		return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Registration request failed !!").build();
        	}
        }
        return Response.status(Status.OK).entity("Registration request successfull !!").build();
	}
	
	/*Receive de-registration request from the LWM2M client's web page*/
	@Path("/Deregistration/{EndPointClientName}")
	@DELETE
	@Produces("application/json")
	public Response sendDeRegistrationReq(@PathParam("EndPointClientName") String endPntCltNm, String DeregistrationReqStr) {
		OutputStreamWriter outputStreamWriter = null;
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        try {
			/*Send de-registration request to the LWM2M server*/
			String urlString = "http://localhost:8080/LWM2M_Server/omaserver/Deregistration/" + endPntCltNm;
			URL url = new URL(urlString);
			HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setDoOutput(true);
			httpURLConnection.setDoInput(true);
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setRequestMethod("DELETE");
			outputStreamWriter = new OutputStreamWriter(httpURLConnection.getOutputStream());
			System.out.println("DeRegistration request from client - "+ endPntCltNm + " is : " + DeregistrationReqStr); 
			outputStreamWriter.write(DeregistrationReqStr);
			outputStreamWriter.flush();

			inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
			StringBuilder stringBuilder = new StringBuilder();
			String line = null;
			while ((line =bufferedReader.readLine()) != null ){
				stringBuilder.append(line);
			}
			/*Obtain the registration response from server and parse the message*/
			System.out.println("Response from server is : " + stringBuilder.toString()); 
			return Response.status(Status.OK).entity("DeRegistration request successfull !!").build();
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Registration request failed !!").build();
        }
	}
	
	@Path("/UpdateRate/{EndPointClientName}")
	@POST
	@Produces("application/json")
	public Response updateProductionRate(@PathParam("EndPointClientName") String endPntCltNm, String AssemblyRate) {
		OutputStreamWriter outputStreamWriter = null;
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        try {
        	String status = new JSONObject(AssemblyRate).getJSONObject("PRODRATEUPDATEREQUEST").getString("STATUS").toString();
			if(!status.equals("STOPPED")){
				/*Send notify rate request to the server*/
				String urlString = "http://localhost:8080/LWM2M_Server/omaserver/NotifyRate/" + endPntCltNm;
				URL url = new URL(urlString);
				HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
				httpURLConnection.setDoOutput(true);
				httpURLConnection.setDoInput(true);
				httpURLConnection.setRequestProperty("Content-Type", "application/json");
				httpURLConnection.setRequestProperty("Accept", "application/json");
				httpURLConnection.setRequestMethod("POST");
				outputStreamWriter = new OutputStreamWriter(httpURLConnection.getOutputStream());
				System.out.println("Update manufaturing rate request from client - "+ endPntCltNm + " is : " + AssemblyRate); 
				outputStreamWriter.write(AssemblyRate);
				outputStreamWriter.flush();
	
				inputStream = new BufferedInputStream(httpURLConnection.getInputStream());
				bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"), 8);
				StringBuilder stringBuilder = new StringBuilder();
				String line = null;
				while ((line =bufferedReader.readLine()) != null ){
					stringBuilder.append(line);
				}
				/*Obtain the registration response from server and parse the message*/
				System.out.println("Response from server is : " + stringBuilder.toString()); 
				return Response.status(Status.OK).entity(stringBuilder.toString()).build();
			} else {
				System.out.println("Assembly Rate not send to server since client is stopped"); 
				return Response.status(Status.OK).entity("Assembly Rate not send to server since client is stopped").build();
			}
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Assembly rate request failed !!").build();
        }
	}	
	
	@Path("/GetStatus")	
	@GET
	public Response getClientStatus() {
		Connection mysqlDBConn = null;
        Statement statement = null;
        ResultSet resultSet = null;
        String dbName = "mysqldb_client2";
        String status = "";
        String current_rate = "";
        String normal_rate = "";
        JSONObject getStatusResponseJSON = null;
		try {
			/*Insert or Update the bootstrap information into the client MySQL Database*/
			Class.forName("com.mysql.jdbc.Driver");
	        String mySQLJDBCUrl = "jdbc:mysql://localhost/" + dbName;
	        String userID = "mysqladmin";
	        String password = "mysqladmin";		

        	mysqlDBConn = DriverManager.getConnection(mySQLJDBCUrl, userID, password);
        	statement = mysqlDBConn.createStatement();
        	String selectStmt = "SELECT clt_status.status, clt_status.current_rate, clt_status.normal_rate  FROM " + dbName +".clt_status ";
        	resultSet = statement.executeQuery(selectStmt); 
			if (resultSet.next()) {
				 status = resultSet.getString("status");
				 current_rate = resultSet.getString("current_rate");
				 normal_rate = resultSet.getString("normal_rate");
				 System.out.println("getClientStatus().Status  is -------------------" + status);
				 System.out.println("getClientStatus().Current_Rate  is -------------------" + current_rate);
				 System.out.println("getClientStatus().Normal_Rate  is -------------------" + normal_rate);
				 JSONObject getStatusResponse = new JSONObject();
				 getStatusResponse.put("STATUS", status);
				 getStatusResponse.put("CURRENT_RATE", current_rate);
				 getStatusResponse.put("NORMAL_RATE", normal_rate);
				 getStatusResponseJSON = new JSONObject();
				 getStatusResponseJSON.put("STATUSRESPONSE", getStatusResponse);
        		 System.out.println("Status of client in MySQL DB (" + dbName +" ) is -------------------" + status);
            }
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Get client status request failed !!").build();
        } finally {
            try {
                if (resultSet != null) { resultSet.close(); }
                if (statement != null) { statement.close(); }
                if (mysqlDBConn != null) { mysqlDBConn.close(); }
            } catch (Exception e) {
            	e.printStackTrace();
            	return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Client-initiated bootstrap request failed !!").build();
            }
        }
        return Response.status(Status.OK).entity(getStatusResponseJSON.toString()).build();
	}	
	
	@Path("/Shutdown")	
	@GET
	public Response performClientShutdown() {
		Connection mysqlDBConn = null;
        Statement statement = null;
        
        String dbName = "mysqldb_client2";
        
		try {
			/*Insert or Update the bootstrap information into the client MySQL Database*/
			Class.forName("com.mysql.jdbc.Driver");
	        String mySQLJDBCUrl = "jdbc:mysql://localhost/" + dbName;
	        String userID = "mysqladmin";
	        String password = "mysqladmin";			
        	mysqlDBConn = DriverManager.getConnection(mySQLJDBCUrl, userID, password);
        	statement = mysqlDBConn.createStatement();
        	String selectStmt = "UPDATE "
        						+ dbName +".clt_status "
			        			+ "SET clt_status.status = 'STOP', clt_status.current_rate = 0";
        	int sqlStatus = statement.executeUpdate(selectStmt);         
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Server-initiated shutdown request failed !!").build();
        } finally {
            try {
                if (statement != null) { statement.close(); }
                if (mysqlDBConn != null) { mysqlDBConn.close(); }
            } catch (Exception e) {
            	e.printStackTrace();
            	return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Server-initiated shutdown request failed !!").build();
            }
        }
        return Response.status(Status.OK).entity("Server-initiated shutdown request successfull !!").build();
	}
	
	@Path("/StartProductionLine")	
	@POST
	public Response startProdLine() {
		Connection mysqlDBConn = null;
        Statement statement = null;
        
        String dbName = "mysqldb_client2";
        
		try {
			/*Insert or Update the bootstrap information into the client MySQL Database*/
			Class.forName("com.mysql.jdbc.Driver");
	        String mySQLJDBCUrl = "jdbc:mysql://localhost/" + dbName;
	        String userID = "mysqladmin";
	        String password = "mysqladmin";			
        	mysqlDBConn = DriverManager.getConnection(mySQLJDBCUrl, userID, password);
        	statement = mysqlDBConn.createStatement();
        	String selectStmt = "UPDATE "
        						+ dbName +".clt_status "
			        			+ "SET clt_status.status = 'ACTIVE', clt_status.current_rate = clt_status.normal_rate";
        	int sqlStatus = statement.executeUpdate(selectStmt);         
        } catch (Exception e) {
           e.printStackTrace();
           return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Production start request failed !!").build();
        } finally {
            try {
                if (statement != null) { statement.close(); }
                if (mysqlDBConn != null) { mysqlDBConn.close(); }
            } catch (Exception e) {
            	e.printStackTrace();
            	return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Production start request failed !!").build();
            }
        }
        return Response.status(Status.OK).entity("Production start request successfull !!").build();
	}
}
