package edu.umass.cs.ripples.paol;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.Lists;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.api.services.calendar.model.CalendarListEntry;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.StringTokenizer;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.io.FileNotFoundException;

/**
 * @author Yaniv Inbar
 * @author Ryan Szeto
 */
public class CalendarParser {

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = "PAOL Calendar Parser";
  
  /** Global instance of the HTTP transport. */
  private static HttpTransport HTTP_TRANSPORT;

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = new JacksonFactory();

  private static com.google.api.services.calendar.Calendar client;
  
  private static final String CAP_SCRIPT = "/home/paol/paol-code/scripts/capture/fullCapture.sh";
  private static final String CRON_TEMP = "/home/paol/paol-code/cron_temp.txt";
  private static final String SEM_DATES = "/home/paol/paol-code/semesterDates.txt";
  
  private static String semester = "default";
  
  // How many seconds before lecture to start capture
  private static final long buffer = 300;
  
  // How many days to look ahead
  private static final int scanPeriod = 3;

  /** Authorizes the installed application to access user's protected data. */
  private static Credential authorize() throws Exception {
    // load client secrets
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
        new InputStreamReader(CalendarParser.class.getResourceAsStream("/client_secrets.json")));
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      println(
          "Enter Client ID and Secret from https://code.google.com/apis/console/?api=calendar "
          + "into calendar-cmdline-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }
    // set up file credential store; info is stored in ~/.credentials/calendar.json
    FileCredentialStore credentialStore = new FileCredentialStore(
        new File(System.getProperty("user.home"), ".credentials/calendar.json"), JSON_FACTORY);
    // set up authorization code flow
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
        Collections.singleton(CalendarScopes.CALENDAR)).setCredentialStore(credentialStore).build();
    // authorize
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
  }

  public static void main(String[] args) {
    try {
      try {
	  
		if(args.length != 1) {
			errPrintln("Exactly one calendar (no spaces) should be specified");
			System.exit(1);
		}

        // initialize the transport
        HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        // authorization
        Credential credential = authorize();

        // set up global Calendar instance
        client = new com.google.api.services.calendar.Calendar.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(
            APPLICATION_NAME).build();

		// get calendars
		Map<String, String> sumryToIdMap = initSumryToIdMap();
		String calKey = null;
		String capComp = args[0]; // computer that is running this code
		if((calKey = sumryToIdMap.get(capComp)) == null) {
			println("Calendar '" + capComp + "' was not found. Here are the available calendars:");
			for(String key : sumryToIdMap.keySet())
				println(key);
			System.exit(1);
		}

		println("Calendar '" + capComp + "' was found. Writing cron lines\n");
		println("Setting semester");
		if(!setSemester(SEM_DATES))
			errPrintln("Failed to set semester");
		Calendar calendar = client.calendars().get(calKey).execute();
		// create file and BufferedWriter
		File cronLines = new File(CRON_TEMP);
		if(cronLines.exists())
			cronLines.delete();
		cronLines.createNewFile();
		BufferedWriter writer = new BufferedWriter(new FileWriter(cronLines));
        writeEvents(calendar, writer);
		writer.close();
		System.exit(0);

      } catch (IOException e) {
        errPrintln(e.getMessage());
      }
    } catch (Throwable t) {
      t.printStackTrace();
    }
    System.exit(1);
  }
  
  public static Map<String, String> initSumryToIdMap() throws IOException {
	HashMap<String, String> ret = new HashMap<String, String>();
	CalendarList feed = client.calendarList().list().execute();
	List<CalendarListEntry> entries = feed.getItems();
	for(CalendarListEntry e : entries)
		ret.put(e.getSummary(), e.getId());
	return ret;
  }

  private static void writeEvents(Calendar calendar, BufferedWriter writer) throws IOException {
    Date start = new Date();
	Date end = new Date(start.getTime() + scanPeriod*24*3600*1000);
	DateTime startDT = new DateTime(start);
	DateTime endDT = new DateTime(end);
    Events feed = client.events().list(calendar.getId()).set("singleEvents", true)
			.set("orderBy", "startTime")
			.set("timeMin", startDT).set("timeMax", endDT).execute();
	List<Event> events = feed.getItems();
	for(Event e : events) {
		if(e.getStart().getDateTime() == null)
			continue;
		long eStartLong = e.getStart().getDateTime().getValue();
		long eEndLong = e.getEnd().getDateTime().getValue();
		long duration = (eEndLong - eStartLong)/1000;
		Date eventStart = new Date(eStartLong);
		println(e.getSummary());
		println("Start: " + eventStart.toString());
		println("Duration (s): " + duration);
		println("Writing cron job:");
		println(cronLine(e, semester));
		System.out.println();
		writeLineToFile(writer, cronLine(e, semester));
	}
	if(events.size() == 0)
		errPrintln("Warning: No events found within the scan period. cron_temp.txt should be empty");
	else
		println("Finished writing lines to file");
  }
  
  private static String cronLine(Event e, String sem) {
	if(e.getStart().getDateTime() == null) {
		errPrintln("[CalendarParser] Tried to parse all-day event");
		return null;
	}
	long eStartLong = e.getStart().getDateTime().getValue();
	long eEndLong = e.getEnd().getDateTime().getValue();
	long duration = (eEndLong - eStartLong)/1000;
	Date sDate = new Date(eStartLong - buffer*1000);
	return cronLine(sDate.getMinutes(), sDate.getHours(), sDate.getDate(), sDate.getMonth(), sDate.getYear(), sem, e.getSummary(), duration+2*buffer);
  }
  
  // m h  dom mon dow year   command
  private static String cronLine(int min, int hr, int dayOfMon, int mon, int year, String sem, String course, long dur) {
	return "#" + min + " " + hr + " " + dayOfMon + " " + (mon+1) + " * " + (year+1900) + " " + CAP_SCRIPT + " " + sem + " " + course + " " + dur;
  }
  
  private static void writeLineToFile(BufferedWriter writer, String line) throws IOException {
	writer.write(line);
	writer.newLine();
  }
  
  private static void println(String toPrint) {
	System.out.println("[CalendarParser] " + toPrint);
  }
  
  private static void errPrintln(String toPrint) {
	System.err.println("[CalendarParser] " + toPrint);
  }
  
  private static boolean setSemester(String datesLoc) throws IOException {
	try {
		File datesFile = new File(datesLoc);
		BufferedReader reader = new BufferedReader(new FileReader(datesFile));
		String line;
		while((line = reader.readLine()) != null) {
			println(line);
			StringTokenizer tokenizer = new StringTokenizer(line);
			if(tokenizer.countTokens() != 3) {
				errPrintln("Malformated semester file");
				return false;
			}
			String sem = tokenizer.nextToken();
			String start = tokenizer.nextToken();
			String end = tokenizer.nextToken();
			if(todayIsInSemester(start, end)) {
				semester = sem;
				return true;
			}
		}
		errPrintln("Today is not within a semester");
		return false;
	} catch (FileNotFoundException e) {
		errPrintln(e.toString());
		return false;
	}
  }
  
  private static boolean todayIsInSemester(String startDay, String endDay) {
	try {
		Date today = new Date();
		String startTime = startDay + " 12:00 AM";
		String endTime = endDay + " 11:59 PM";
		SimpleDateFormat df = new SimpleDateFormat();
		Date s = df.parse(startTime);
		Date e = df.parse(endTime);
		return today.compareTo(s) >= 0 && today.compareTo(e) <= 0;
	} catch (ParseException e) {
		errPrintln(e.toString());
		System.exit(1);
	}
	return false;
  }

}