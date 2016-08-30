/*
 * Copyright (C) 2016 Toby Scholz 2016
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package NepTune;

import java.io.IOException;
import java.io.OutputStream;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.List;
import org.bson.Document;
import org.bson.json.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 
 * Handles the interaction with the client by way of HTTP GET requests
 * Examples:
 * http://<serverUrl>:<serverPort>?mode=data return JSON object of all data
 * See processReq method for details
 * 
 * TODO: implement HTTP POST
 */
public class ClientConnector implements HttpHandler, Runnable {
    
    Socket socket;
    private static final String CHARSET = java.nio.charset.StandardCharsets.UTF_8.name(); // UTF8 is used for NepTune
    private static final int STATUS_OK = 200; 
    private final static String CRLF = "\r\n"; // html new line
    DataStoreSingleton dataStore = DataStoreSingleton.getInstance();
    ConfigReaderSingleton properties = ConfigReaderSingleton.getInstance();
    private final int waitBeforeSendingPlaylist; 
    
    ClientConnector(Socket sock)  
    {
        socket = sock;
        waitBeforeSendingPlaylist = Integer.parseInt(properties.getProperty("waitBeforeSendingPlaylist"));
        LOG.INFO("ClientConnector: Connection accepted from host: " + socket.getInetAddress().getHostName() + "," + socket.getInetAddress().getHostAddress());
    }
    
    /**
     * Required override
     * @param httpExchange
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange httpExchange) throws IOException 
    {
        String response = "This is the response";
        httpExchange.sendResponseHeaders(STATUS_OK, response.length());
        OutputStream os = httpExchange.getResponseBody();
        os.write(response.getBytes(Charset.forName("UTF-8")));
        os.close();
    }
    
    
    @Override
    public void run()
    {
        try 
        {
            processReq();
        }
        catch(Exception e) 
        {
            LOG.ERROR(e.toString());
        }
    }
    
    /**
     * Modes:
     * data: returns complete data set
     * http://<serverUrl>:<serverPort>?mode=data
     * 
     * search: searches for string in data set
     * http://<serverUrl>:<serverPort>?mode=search&searchString=%20search terms%20
     * (search terms enclosed in "" are treated as single string)
     * 
     * playlist: returns m3u8 playlist used for HTTP live streaming
     * http://<serverUrl>:<serverPort>?mode=playlist&objectId=57ae33d69041ea0bef07490a
     * 
     * file: returns any file directly from samba share
     * http://<serverUrl>:<serverPort>?mode=file&objectId=57ae33d69041ea0bef07490a
     * If request ends with .ts, returns HLS files from tmp directory, e.g.
     * http://<serverUrl>:<serverPort>?mode=file&objectId=af56cc43.ts
     * 
     * helloWorld: returns string Welcome! Can be used to check connection
     * http://<serverUrl>:<serverPort>?mode=helloWorld
     */ 
    
    // Should refactor this to reduce code duplication
    // Should be called via connectionHandler, never directly.
    private void processReq() throws Exception
    {
        InputStream istream = socket.getInputStream();
        DataOutputStream ostream = new DataOutputStream(socket.getOutputStream());
        BufferedReader breader = new BufferedReader(new InputStreamReader(istream, CHARSET));
        String line = breader.readLine(); 
        String connectionDetails = breader.readLine();
        LOG.DEBUG("Connection to " + connectionDetails.substring(connectionDetails.indexOf(":") + 2));
        Map<String, String> urlParameters = parseUrl(line);
        LOG.DEBUG("Request from client was: " + urlParameters);
        List<Document> docs = null;
        String status = null;
        String contentType = null;
        String encoding = "charset: " + "UTF-8" + CRLF;
        
        String mode = urlParameters.get("mode");
        LOG.DEBUG("Query mode is " + mode);
        
        /**Returns the whole data set */
        if ( mode.equals("data") )
        {
            docs = dataStore.getList();
            status = "HTTP/1.1 200 OK" + CRLF; 
            contentType = "Content-type: " + "application/x-mpegURL" + CRLF;
        }
        
        /**Returns the data set that contains the supplied search strings */
        else if (mode.equals("search"))
        {
            String searchString = URLDecoder.decode(urlParameters.get("searchString"), CHARSET);
            LOG.DEBUG("Search string is " + searchString);
            List<String> searchList = new ArrayList<String>();
            Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
            Matcher regexMatcher = regex.matcher(searchString); //http://stackoverflow.com/questions/366202/regex-for-splitting-a-string-using-space-when-not-surrounded-by-single-or-double
            while (regexMatcher.find()) 
            {
                if (regexMatcher.group(1) != null) 
                {
                    // Add double-quoted string without the quotes, e.g. "apple pie"
                    searchList.add(regexMatcher.group(1));
                } 
                else if (regexMatcher.group(2) != null) 
                {
                    // Add single-quoted string without the quotes, e.g. 'apple pie'
                    searchList.add(regexMatcher.group(2));
                } else 
                {
                    // Add unquoted word, e.g. apple pie (two words, "apple", and "pie", separated by space)
                    searchList.add(regexMatcher.group());
                }
            } 
            searchList.stream().forEach((String string) -> {LOG.DEBUG("Search term: " + string);}) ;
            docs = search(searchList);
            status = "HTTP/1.1 200 OK" + CRLF; 
            //contentType = "Content-type: " + "text/html; charset=UTF-8" + CRLF + CRLF; // for testing in browser
            contentType = "Content-type: " + "application/x-mpegURL" + CRLF; // proper json type
        }
        
        /**Returns an m3u8 playlist, and starts the transcoding process */
        else if (mode.equals("playlist"))
        {
            String objectId = URLDecoder.decode(urlParameters.get("objectId"), CHARSET); //http://stackoverflow.com/questions/15235400/java-url-param-replace-20-with-space
            Document doc = dataStore.getMap().get(objectId);
            String path = (String) doc.get("filePath");
            String file = (String) doc.get("fileName");
            String type = (String) doc.get("fileType");
            String filePath = path + file + "." + type;                    
            String expectedPlaylistPath = properties.getTempDirectory() + objectId + ".m3u8";
            LOG.DEBUG("Requested objectId is " + objectId);
            File expectedPlaylist = new File(expectedPlaylistPath);
            if (!expectedPlaylist.exists()) // If already exists, HLScreator already running
            {
                try
                {
                    String targetUrl = "http://" + connectionDetails.substring(connectionDetails.indexOf(":") + 2) + "";
                    if(urlParameters.containsKey("targetUrl"))
                    {
                        String url = URLDecoder.decode(urlParameters.get("targetUrl"), CHARSET);
                        targetUrl = url.substring(0, url.indexOf("?"));
                    }
                    HLSCreator hc = new HLSCreator(objectId, type, targetUrl);
                    Thread hcThread = new Thread(hc);
                    hcThread.start();
                }
                catch(Exception e)
                {
                    new ErrorSender(e.toString(), socket, ostream).sendError();
                    LOG.ERROR("Failed to create HLS stream " + e.toString());
                }
            }
 
            LOG.DEBUG("M3U8 requested");
            int count = 0;
            while(!expectedPlaylist.exists() && count <= 500) //Has FFMpeg created it yet?
            {
                Thread.sleep(50);
                count++;
            }
            if (count > 499)
            {
                new ErrorSender("Could not find playlist", socket, ostream).sendError(); // To client
                LOG.ERROR("Could not find playlist for id " + objectId); // To log file
                return; // Stop it from locking
            }
            
            BufferedReader br = new BufferedReader(new FileReader(expectedPlaylist));
            // Let's check how much of the playlist is already written
            String newLine;
            while(true)
            {
                newLine = br.readLine();
                if (newLine.startsWith("#EXTINF:")) // Entry for first stream file should be in next line
                {
                    // Safe to send it to the client
                    break;
                }
            }
            br.close();
            
            Thread.sleep(waitBeforeSendingPlaylist); // Give FFmpeg a head start with encoding
            File actualPlaylist = new File(expectedPlaylistPath); // Re-read file, workaround...
            byte [] bytearray  = new byte [(int)actualPlaylist.length()];
            FileInputStream fis = new FileInputStream(actualPlaylist);
            LOG.DEBUG("Requested File is " +  actualPlaylist.getCanonicalPath());
            BufferedInputStream bis = new BufferedInputStream(fis);
            bis.read(bytearray, 0, bytearray.length);
            status = "HTTP/1.1 200 OK" + CRLF; 
            contentType = "Content-type: " + "application/x-mpegURL" + CRLF + CRLF;
            
            ostream.write(status.getBytes(Charset.forName("UTF-8")));
            ostream.write(contentType.getBytes(Charset.forName("UTF-8")));
            ostream.write(bytearray,0,bytearray.length);
            LOG.DEBUG("M3U8 file" + System.lineSeparator() + new String(bytearray) + System.lineSeparator());
            ostream.close();
            bis.close();
            breader.close();
            LOG.DEBUG("M3U8 delivered");
            return;
        }
        
        /** Returns any old file, taking type from metadata (as parsed by metadataparser) */
        else if (mode.equals("file"))    
        {
            String filePath;
            String objectId = URLDecoder.decode(urlParameters.get("objectId"), CHARSET);
            if (objectId.endsWith(".ts")) // HLS parts
            {
                String expectedFilePath = properties.getTempDirectory() + objectId;
                LOG.DEBUG("Expected file is " + expectedFilePath);
                File streamFile = fileReadyForTransmission(new File(expectedFilePath));
                byte [] bytearray  = new byte [(int)streamFile.length()];
                FileInputStream fis = new FileInputStream(streamFile);
                LOG.DEBUG("Requested File is " +  streamFile.getCanonicalPath());
                BufferedInputStream bis = new BufferedInputStream(fis);
                bis.read(bytearray, 0, bytearray.length);
                status = "HTTP/1.1 200 OK" + CRLF; 
                contentType = "Content-type: " + "video/MP2T" + CRLF + CRLF;
                
                ostream.write(status.getBytes(Charset.forName("UTF-8")));
                ostream.write(contentType.getBytes(Charset.forName("UTF-8")));
                ostream.write(bytearray,0,bytearray.length);
                ostream.close();
                bis.close();
                breader.close();
                return;
            }
            else // Requested was a file from the index
            {
                Document doc = dataStore.getMap().get(objectId);
                String path = (String) doc.get("filePath");
                String file = (String) doc.get("fileName");
                String type = (String) doc.get("fileType");
                contentType = "Content-type: " + doc.get("Content-Type") + ";" + CRLF + CRLF;
                filePath = path + file + "." + type;
            }
            try
            {
                FileStreamer fs = new FileStreamer(filePath, socket, istream, contentType); 
                Thread fileThread = new Thread(fs);
                fileThread.start();
                return;
            } 
            catch(Exception e)
            {
                new ErrorSender(e.toString(), socket, ostream).sendError();
                LOG.ERROR("Cannot find file " + e.getLocalizedMessage());
                return;
            }
        }
        
        /** Just to say hello - use to check connection */
        else if (mode.equals("helloWorld"))
        {
            status = "HTTP/1.1 200 OK" + CRLF; 
            contentType = "Content-type: " + "text/html; charset=UTF-8" + CRLF + CRLF;
            
            ostream.write(status.getBytes(Charset.forName("UTF-8")));
            ostream.write(contentType.getBytes(Charset.forName("UTF-8")));
            ostream.write("Welcome!".getBytes(Charset.forName("UTF-8")));
            ostream.close();
            breader.close();
            return;
        }
        
        else 
        {
            new ErrorSender("Mode not recognized", socket, ostream).sendError();
            LOG.ERROR("Mode " + mode + " not recognized");
            return;
        }

        ostream.write(status.getBytes(Charset.forName(CHARSET)));
        ostream.write(contentType.getBytes(Charset.forName(CHARSET)));
        ostream.write(encoding.getBytes(Charset.forName(CHARSET)));
        ostream.write(CRLF.getBytes(Charset.forName(CHARSET))); // end of header
        
        //finalizing the JSON object for sending
        if (mode.equals("search"))
            {
            PrintWriter writer = new PrintWriter(ostream);
            JsonWriterSettings settings = new JsonWriterSettings(JsonMode.STRICT); // must be strict
            writer.write("[");
            try 
            {
                for (Document doc : docs )
                {
                    writer.print(doc.toJson(settings));
                    writer.write(",");
                    writer.write(CRLF);
                }
            }
            catch(Exception e)
            {
                new ErrorSender(e.toString(), socket, ostream).sendError();
            }
            finally
            {
                writer.write("]");
                writer.flush();
                writer.close();
            }
        }
        
        ostream.close();
        breader.close();
        socket.close();    
    } //end processReq
    
    /** Slow search implementation. Handy for testing, but not normally used */
    private synchronized List<Document> simpleSearch(List<String> searchText)
    {
        //LOG.DEBUG("SearchText is " + searchText);
        List<Document> data = dataStore.getList();
        List<Document> result = new ArrayList<>();
        
        for (Document doc : data )
        {
            Set<String> setOfKeys = doc.keySet();
            boolean found = false;
            for ( String key : setOfKeys )
            {
                String val = doc.get(key).toString();
                //LOG.DEBUG("Value is " + val);
                for(String searchString : searchText)
                {
                    if ( doc.get(key).toString().toLowerCase().contains(searchString.toLowerCase()))
                    {
                        result.add(doc);
                        found = true;
                        break; // else we get multiple copies, unnecessary
                    }
                }
                if (found)
                {
                    break;
                }
            }
        }
        LOG.DEBUG("Result is: " + result.toString());
        return result;
    }
    
    /**Searches a document for searchStrings */
    private List<Document> search(List<String> searchStrings) throws InterruptedException, ExecutionException
    {
        //return simpleSearch(searchStrings); //slow search, single threaded on values (handy for testing)
        SpeedyGonzales sg = new SpeedyGonzales(); //fast, multithreaded search 
        sg.setPriority(Thread.MAX_PRIORITY); // ensure search is always fast
        return sg.findString(searchStrings);
    }

    
    /**
     * Checks if file is locked by another application
     * @param file: file to check
     * @return: checked file
     * @throws IOException 
     * Adapted from http://stackoverflow.com/questions/15978064/java-filelock-blocking-without-exceptions-waiting-on-the-lock
     */
    private File fileReadyForTransmission(File file) throws InterruptedException {
    FileLock lock = null;
    FileChannel channel = null;
    
    if (file.lastModified() != 0L) // Has attribute last modified
    {
        while( LOG.NOW().getTime() < (file.lastModified() + 50) ) // Has been modified in last 50ms - wait a bit
        {
            Thread.sleep(10);
        }
    }
    try 
    {
       channel = new RandomAccessFile(file, "rw").getChannel();
       lock = channel.lock();
       LOG.DEBUG("Channel locked");
       int count = 0;
       while(!file.canWrite() && count < 500)
       {
           Thread.sleep(50);
       }
       lock.release();
       LOG.DEBUG("Channel released");
    }
    catch(IOException e)
    {
        LOG.ERROR("Error accessing file " + e.toString());
    }            
    finally
    {
       if (channel != null)
       {
           try 
           {    
               channel.close();
           }
           catch(Exception e) {}; // would have previously thrown
       }
    }
    return file;
    } //end fileReadyForTransmission
    
    /**Parses URL query into parameter map. */
    private Map<String, String> parseUrl(String url) throws UnsupportedEncodingException
    {
        url = url.substring(url.indexOf("?") + 1, url.indexOf("HTTP/") -1); // Only query parameters
        final Map<String, String> parameters = new LinkedHashMap<>();
        final String[] content = url.split("&");
        for (String kv : content)
        {
            int index = kv.indexOf("=");
            String key = URLDecoder.decode(kv.substring(0, index), "UTF-8");
            parameters.put(key, kv.substring(index + 1));         
        }
        LOG.DEBUG("Parameters are " + parameters.toString());
        return parameters;
    } //end parseUrl
    
} //end class