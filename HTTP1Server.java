import java.lang.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.text.ParseException;

public class HTTP1Server implements Runnable {

	private Socket csocket;
	private byte[] fileBytes;
	private String command;
	private String requestHeader;
	private String[] headers;
	private String[] tokens;
	private static int portNum;

	HTTP1Server (Socket csocket) {
		this.csocket = csocket;
	}

	//Check validity of port input to make sure it's an integer
	private static int checkPortInput(String input) {
		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static void main (String[] args) {

		//Check validity of arguments
		if (args.length != 1) {
			System.out.println("Error: Invalid input. Please use java SimpleHHTPServer <port>");
			return;
		}

		portNum = checkPortInput(args[0]);
		ServerSocket ssock;

		//If port arg is not an integer, return error
		if(portNum == -1)
			System.out.println("Error: Invalid format for port number. Please use an integer.");
		else
		{
			//Otherwise, create server socket and begin listening
			ExecutorService threadPoolExecutor =
					new ThreadPoolExecutor(
							5, 50, 5000, TimeUnit.MILLISECONDS,
							new SynchronousQueue<Runnable>());
			try {
				ssock = new ServerSocket(portNum);
				System.out.println("Listening on Port Number " + portNum);

				while(true) {
					//When a client connects, spawn a new thread to handle it
					Socket sock = ssock.accept();
					System.out.println("Connected!");
					try {
						threadPoolExecutor.execute(new HTTP1Server(sock));
					} catch (RejectedExecutionException rej) {
						try {
						//PrintWriter outToClient = new PrintWriter(sock.getOutputStream(), true);
						//outToClient.println("503 Service Unavailable");
						//outToClient.close();
							System.out.println("Service is unavailable!");
							DataOutputStream outToClient = new DataOutputStream(sock.getOutputStream());
							byte[] byteResponse = "HTTP/1.0 503 Service Unavailable".getBytes();
							outToClient.write(byteResponse);
							outToClient.close();
							sock.close();
						} catch (IOException f) {
							System.out.println("Error handling client input.");
						}
					}
					//new Thread(new SimpleHTTPServer(sock)).start();
				}
			} catch (IOException e) {
				System.out.println("Error accepting connection.");
			}
		}
	}

	//Check extension for content-type
	private String contentType(String extension) {

		String contentType = "";

		switch(extension) {

		case "html":
			contentType = "text/html";
			break;
		case "c":
		case "c++":
		case "cc":
		case "com":
		case "conf":
		case "cxx":
		case "def":
		case "f":
		case "f90":
		case "for":
		case "g":
		case "h":
		case "hh":
		case "idc":
		case "jav":
		case "java":
		case "list":
		case "log":
		case "lst":
		case "m":
		case "mar":
		case "pl":
		case "sdml":
		case "text":
		case "txt":
			contentType = "text/plain";
			break;
		case "gif":
			contentType = "image/gif";
			break;
		case "jfif":
		case "jfif-tbnl":
		case "jpe":
		case "jpeg":
		case "jpg":
			contentType = "image/jpeg";
			break;
		case "png":
		case "x-png":
			contentType = "image/png";
			break;
		case "pdf":
			contentType = "application/pdf";
			break;
		case "gz":
		case "gzip":
			contentType = "application/x-gzip";
			break;
		case "zip":
			contentType = "application/zip";
			break;
		default:
			contentType = "application/octet-stream";
			break;

		}

		return contentType;

	}

	//Generate header based on filename and file
	private String buildHeader (File file, String fileName) {
		String header = "";

		String extension = "";

		if (fileName.indexOf('.') != -1)
			extension = fileName.substring(fileName.indexOf('.')+1);
		header += "Content-Type: " + contentType(extension) + '\r' + '\n';
		header += "Content-Length: " + file.length() + '\r' + '\n';

		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

		header += "Last-Modified: " + sdf.format(file.lastModified()) + '\r' + '\n';
		header += "Content-Encoding: identity" + '\r' + '\n';
		header += "Allow: GET, POST, HEAD" + '\r' + '\n';

		Calendar now = Calendar.getInstance();
		now.add(Calendar.HOUR, 24);
		header += "Expires: " + sdf.format(now.getTime()) + '\r' + '\n';



		return header;


	}

	//Check the command to determine if it's 400 BAD Request, 200 OK, or 501 Not Implemented
	private int checkCommand(String command) {
		if (command.equals("GET") || command.equals("POST") || command.equals("HEAD"))
			return 0;
		else if (command.equals("PUT") || command.equals("DELETE") || command.equals("LINK") || command.equals("UNLINK"))
			return 1;
		else
			return 2;
	}

	private String parseClientInput(String clientInput) {
		if (clientInput == null)
			return "HTTP/1.0 400 Bad Request";

		tokens = clientInput.split("\\s+");

		float versionNum;

		//1. Parse format. Only three tokens, and first token is capitalized? Does the second token begin with /? Does the third token begin with HTTP/?
		if(tokens.length != 3 || !tokens[0].toUpperCase().equals(tokens[0]) || tokens[1].charAt(0) != '/' || !tokens[2].substring(0,5).equals("HTTP/") || tokens[2].substring(5) == null)
			return "HTTP/1.0 400 Bad Request";

		//Try to grab version number and see if it's valid
		try {
			versionNum = Float.parseFloat(tokens[2].substring(5));
		} catch (NumberFormatException num) {
			return "HTTP/1.0 400 Bad Request";
		}

		//See if version number is supported
		if (versionNum > 1.0 || versionNum < 0.0)
			return "HTTP/1.0 505 HTTP Version Not Supported";

		//2. Parse command. Is the first token GET, POST or HEAD?
		if(checkCommand(tokens[0]) == 1)
			return "HTTP/1.0 501 Not Implemented";
		else if (checkCommand(tokens[0]) == 2)
			return "HTTP/1.0 400 Bad Request";

		//3. Parse file. Does the file exist on the server?
		String filePath = "." + tokens[1];

		File f = new File(filePath);
		if (!f.isFile())
			return "HTTP/1.0 404 Not Found";

		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

		int modifiedIndex = -2;
		//If If-Modified-Since header exists on a GET request, see if file has been modified since the requested date
		if ((modifiedIndex = findHeaderIndex("If-Modified-Since")) >= 0 && tokens[0].equals("GET")) {
		//if (header.contains("If-Modified-Since") && tokens[0].equals("GET")) {
			String date = headers[modifiedIndex].substring(headers[modifiedIndex].indexOf(':') + 2);
			System.out.println("\n\nModified Since String: " + date + "\n\n");
			String fileDate = sdf.format(f.lastModified());
			try {
				Date fileModifiedDate = sdf.parse(fileDate);
				Date ifModifiedSinceDate = sdf.parse(date);
				if (ifModifiedSinceDate.after(fileModifiedDate)) {
					Calendar now = Calendar.getInstance();
					now.add(Calendar.HOUR, 24);
					return "HTTP/1.0 304 Not Modified" + '\r' + '\n' + "Expires: " + sdf.format(now.getTime()) + '\r' + '\n' ;
				}
			} catch (ParseException e) {
				System.out.println("Error parsing date");
			}

		}

		Path path = Paths.get(filePath);
		String response = "";

		//4. Otherwise, try to open the file and send response.
		try {

			if (tokens[0].equals("GET") || tokens[0].equals("HEAD")) {
				fileBytes = Files.readAllBytes(path);


				response = "HTTP/1.0 200 OK" + '\r' + '\n';

				//Attach header to the 200 OK Request
				response += buildHeader(f, tokens[1]);


				response+="\r\n";

			}
			else if (tokens[0].equals("POST")) {

				if(!(tokens[1].substring(tokens[1].indexOf('.')+1)).equals("cgi"))
					return "HTTP/1.0 405 Method Not Allowed";

				if (!f.canExecute())
					return "HTTP/1.0 403 Forbidden";

				int contentTypeIndex = -2;
				int contentLengthIndex = -2;

				contentLengthIndex = findHeaderIndex("Content-Length");

				if (contentLengthIndex == -1 || checkPostContentLength(headers[contentLengthIndex].substring(headers[contentLengthIndex].indexOf(':') +2)) < 0)
					return "HTTP/1.0 411 Length Required";

				contentTypeIndex = findHeaderIndex("Content-Type");

				if (contentTypeIndex == -1 || !headers[contentTypeIndex].equals("Content-Type: application/x-www-form-urlencoded"))
					return "HTTP/1.0 500 Internal Server Error";

				String postResponse = startPostProcess();
				int postLength = 0;
				if (postResponse == null)
					return "HTTP/1.0 204 No Content";
				else if (postResponse != null)
					postLength = postResponse.length();

				response = "HTTP/1.0 200 OK" + '\r' + '\n' + "Content-Type: text/html" + '\r' + '\n' + "Content-Length: " + postLength + '\r' + '\n' +
						"Content-Encoding: identity" + '\r' + '\n' + "Allow: GET, POST, HEAD" + '\r' + '\n' + "Expires: ";

				Calendar now = Calendar.getInstance();
				now.add(Calendar.HOUR, 24);
				response+= sdf.format(now.getTime()) + '\r' + '\n' + '\r' + '\n' + postResponse;

			}

		} catch (AccessDeniedException e) {
			//5. Did the user not have read permissions on the file?
			return "HTTP/1.0 403 Forbidden";
		} catch (IOException io) {
			//6. Did anything go wrong with the request?
			return "HTTP/1.0 500 Internal Server Error";
		}

		return response;

	}

	private String startPostProcess() {

		//Grab body and environment variables
		//Start processbuilder
		//Set environment variables
		//Start process
		//Capture results
		//Return response

		String body = "";
		String result = "";

		if (headers[headers.length-2].equals("")) {
			try {
				body = URLDecoder.decode(headers[headers.length-1], "UTF-8");
				System.out.println("Decoded URL: " + body);
			} catch (UnsupportedEncodingException e) {
				System.out.println("Error decoding URL");
			}
		}

		String cgiPath = "." + tokens[1];

		//Environment variables
		String contentLength = Integer.toString(body.length());
		System.out.println("Content Length: " + contentLength);

		try {

		ProcessBuilder pb = new ProcessBuilder(cgiPath);
		//pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		//pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		Map<String, String> env = pb.environment();

		env.put("CONTENT_LENGTH", contentLength);
		env.put("SCRIPT_NAME", tokens[1]);

		try {
			env.put("SERVER_NAME", InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		env.put("SERVER_PORT", Integer.toString(portNum));

		int httpFromIndex = -2;
		int httpUserAgentIndex = -2;

		if ((httpFromIndex = findHeaderIndex("From:")) >= 0) {
			env.put("HTTP_FROM", headers[httpFromIndex].substring(headers[httpFromIndex].indexOf(':') + 2));
		}

		if ((httpUserAgentIndex = findHeaderIndex("User-Agent:")) >= 0) {
			env.put("HTTP_USER_AGENT", headers[httpUserAgentIndex].substring(headers[httpUserAgentIndex].indexOf(':') + 2));
		}


		Process p = pb.start();

		System.out.println("Process is running!");

		OutputStream stdin = p.getOutputStream();
		stdin.write(body.getBytes());
		stdin.close();

		BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));

		result = stdout.readLine();

		while (stdout.ready()) {
			result+='\n' + stdout.readLine();
		}

		System.out.println("Response from CGI:\r\n" + result);
		stdout.close();

		} catch (IOException e) {
			System.out.println("Error building and running process");
		}

		return result;

	}


	private int findHeaderIndex(String headerVal) {
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].contains(headerVal))
				return i;
		}

		return -1;
	}

	private int checkPostContentLength(String length) {
		try {
			return Integer.parseInt(length);
		} catch (NumberFormatException e) {
			return -1;
		}
	}



	public void run() {
		try {
			//Read in input from client, parse input, return proper code
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(csocket.getInputStream()));
			DataOutputStream outToClient = new DataOutputStream(csocket.getOutputStream());
			command = "";
			requestHeader = "";
			//int numLines = 0;

			try {
				//Set server socket timeout
				csocket.setSoTimeout(3000);
				//Read in message from client
				command = inFromClient.readLine();

				while(inFromClient.ready()) {
					requestHeader+=inFromClient.readLine() + '\r' + '\n';
				}

				System.out.println("Command: " + command);

				headers = requestHeader.split("\r\n");
				//for (int i = 0; i < headers.length; i++) {
				//	System.out.println("Line #" + i + ": " + headers[i]);
				//}
				/*while (numLines != 2) {
					if (numLines == 0)
						command = inFromClient.readLine();
					else if (numLines == 1)
						modified = inFromClient.readLine();
					numLines++;
				}*/
				//System.out.println("Num Lines: " + numLines);
				//Parse input from client
				String response = parseClientInput(command);
				System.out.println("Sending response: " + response);
				//Send response code to client
				byte[] byteResponse = response.getBytes();
				outToClient.write(byteResponse);
				outToClient.flush();

				//If 200 OK and a GET or POST request, send the payload
				if(response.contains("200 OK") && (command.contains("GET")))
					outToClient.write(fileBytes);


			} catch (SocketTimeoutException e){
				//If client does not send data in 3 seconds, send back timeout error
				byte[] byteResponse = "HTTP/1.0 408 Request Timeout".getBytes();
				outToClient.write(byteResponse);
			}
			//Close connections
			inFromClient.close();
			outToClient.close();
			csocket.close();
		} catch (IOException e) {
			System.out.println("Error handling client input.");
		}
	}



}