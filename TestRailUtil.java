package com.gurock.testrail;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/*
 * Utility Class to handle calls to testrail API
 * 
 * 	Test class calls initTestRailRun to initialize a new test run if it hasn't already.
 * 	getDailyRunTest checks if a testRun has already been initialized for the day.
 *  if so then it returns it's ID, else it returns -1 and calls addRun.
 *  addRun initializes a new testrun in testrail, returning it's ID.
 *  getTests then reads in all the tests case ID's in the test suite, into instance hashmap.
 *  Test class then calls postTestResult to push out the results of a specific test to testrail.
 * 
 */
public class TestRailUtil {
	
	private static String [] loginData = getTestRailCredentials();
	private static APIClient client = new APIClient("https://teambrix.testrail.net/");
	private static HashMap<Long, Long> testCaseMap = new HashMap<Long, Long>();
	private static long testRailTestRunID = -1;
	
	
	
	public static void initTestRailRun(String runName, int projectID){
		
		System.out.println("[TestRail] Initializing testrail assests");
		System.out.println("[TestRail] Username: " + loginData[0]);
		System.out.println("[TestRail] Password: " + loginData[1]);
		
		client.setUser(loginData[0]);
		client.setPassword(loginData[1]);
		
		testRailTestRunID = getDailyRunTest(projectID);	
		// Checks to see if TestRun for the day has already been initialized
		if(testRailTestRunID == -1){					
			String timeStamp = new SimpleDateFormat("MM/dd/yyyy HH:mm").format(Calendar.getInstance().getTime());
			testRailTestRunID = addRun(runName + ": " + timeStamp, projectID);		// if not then initialize new one
		}
		getTests(testRailTestRunID);
		System.out.println("[TestRail] TestRun initialized.");
	}
	
	
	public static void getTestCaseInfo(int testCaseID){

		SimpleDateFormat sdf = new SimpleDateFormat();
		
		try {
			JSONObject json = (JSONObject) client.sendGet("get_case/" + testCaseID);
			//System.out.println(json.toJSONString());
			System.out.println("\n--- Test Rail Test Info ---");
			
			System.out.println("Date Created: " + sdf.format(json.get("created_on")));
			System.out.println("Updated On: " + sdf.format(json.get("updated_on")));

			System.out.println("Title: " + json.get("title"));
			System.out.println("Description: " + json.get("custom_expected"));
			System.out.println("Id: " + json.get("id"));
			System.out.println("Suite Id: " + json.get("suite_id"));

		}
		catch (Exception e) {
			e.printStackTrace();
		}
	    
	}
	
	/*
	 * Reads testrail username and password from property file
	 */
	private static String[] getTestRailCredentials(){
			
		String[] credentials = new String[2];
		Properties prop = new Properties();

		try {
			prop.load(new FileInputStream("src/main/resources/config.properties"));
			credentials[0] = prop.getProperty("testrail.user");
			credentials[1] = prop.getProperty("testrail.apikey");

		} catch (IOException ex) {
			ex.printStackTrace();
		} 
		
	return credentials;
	}
	
	/*
	 * TestCase ID links to testrail ID, status code 0 fail, 1 pass.
	 */
	public static void postTestCaseResult(int testCaseID, int statusID, String message){
		
		Map<Object, Object> testCaseData = new HashMap<Object, Object>();

		testCaseData.put("status_id", new Integer(statusID));
		testCaseData.put("comment", "Automated test - " + message);
		
		try{
			JSONObject r = (JSONObject) client.sendPost("add_result/" + getTestRunCase(testCaseID), testCaseData);
			//System.out.println(r.toJSONString());
		}
			catch (Exception e) {
				e.printStackTrace();
			}
	}
	
	/*
	 * Creates new test Run for test instance.
	 * Returns the ID of the new created Run.
	 */
	private static long addRun(String runName, int projectID ) {
	
		Map<Object, Object> testCaseData = new HashMap<Object, Object>();
		
		testCaseData.put("name", runName);
		testCaseData.put("include_all", true);
		
		try{
			JSONObject json = (JSONObject) client.sendPost("add_run/" + projectID, testCaseData);
			//System.out.println(json.toJSONString());
			
			return (long)json.get("id");
		}
		catch (Exception e) {
			e.printStackTrace();
			return -1;
		}	
	}
	
	/*
	 * Queries TestRail to see if a run had already been initialized for the day.
	 * If so then it returns it's ID. Otherwise returns -1;
	 * 
	 */
	public static long getDailyRunTest(int projectID){

		long startOfDayTime = getStartOfDay();

		client.setUser(loginData[0]);
		client.setPassword(loginData[1]);
		
		
		try{
			JSONArray jsonArray = (JSONArray) client.sendGet("get_runs/" + projectID +"&created_after=" + startOfDayTime);
			if(jsonArray.isEmpty())
				return -1;
			else{
				JSONObject jsonObj = (JSONObject)jsonArray.get(0);
				return (long) jsonObj.get("id");
			}
		}
		catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return -1;
		}
	}
		
	private static long getStartOfDay(){
		Calendar date = new GregorianCalendar();
		// reset hour, minutes, seconds and millis
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		return date.getTimeInMillis()/1000;
	}
	
	/*
	 * Gets the tests (Id's) of the particular test run
	 */
	private static void getTests(long testRailTestRunID){

		try {
			JSONArray json = (JSONArray) client.sendGet("get_tests/" + testRailTestRunID);
			//System.out.println(json.toJSONString());
			populateTestMap(json);
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	    
	}
	
	/*
	 * Populates HashMap with the Test Case ID as the key and
	 * the instance of the test Case Run as the value
	 */
	private static void populateTestMap(JSONArray jsonArray){
				
		for(Object ds : jsonArray){
			JSONObject json = (JSONObject) ds;
			testCaseMap.put((long) json.get("case_id"),  // General ID of the test case
							(long)json.get("id"));		 // ID of the case ID in the particular run 
		}
	}
	
	private static long getTestRunCase(long caseID){
		if(testCaseMap.containsKey(caseID))
			return testCaseMap.get(caseID);
		else{
			System.out.println("[TESTRAIL] Test Case: " + caseID + "Does not exist in test suite.");
			return -1;
		}
	}
	
	public static boolean testExists(int testCase){
		return testCaseMap.containsKey(testCase);
	}
	
}
