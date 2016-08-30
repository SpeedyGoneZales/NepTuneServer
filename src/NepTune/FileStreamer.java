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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.Charset;
import jcifs.smb.SmbFile;

/**
 * Returns the requested file to the client via HTTP
 * 
 */
public class FileStreamer implements Runnable {
    
    String filePath;
    ConfigReaderSingleton properties = ConfigReaderSingleton.getInstance();
    SambaConnector samba = new SambaConnector(
    properties.getProperty("sambaUser"), 
    properties.getProperty("sambaPassword"), 
    properties.getProperty("sambaServer"),
    properties.getProperty("sambaPath"));
    private final static String CRLF = "\r\n";
    Socket socket;
    DataOutputStream ostream;
    InputStream istream;
    BufferedReader breader;
    String contentType = "";
    
    /**
     * 
     * @param fp: samba path to file
     * @param sock: socket client connects to
     * @param is: only here so we can close it once file is sent (may not need it?)
     * @param contentType: content type of file to be sent (from metadata)
     * @throws IOException 
     */
    FileStreamer(String fp, Socket sock, InputStream is, String contentType) throws IOException
    {
        filePath = fp;
        socket = sock;
        istream = is;
        ostream = new DataOutputStream(socket.getOutputStream());
        breader = new BufferedReader(new InputStreamReader(istream, "UTF-8"));
        this.contentType = contentType;
    }
    
    @Override
    public void run()
    {
        try 
        {
            SmbFile smbFile = samba.getFile(filePath);
            LOG.DEBUG("Requested File is " + filePath);
            byte [] bytearray  = new byte [(int)smbFile.length()];
            
            if (smbFile.getLastModified() != 0L) // Has attribute last modified
            {
                while( LOG.NOW().getTime() < (smbFile.getLastModified() + 100) ) // Has been modified in last 100ms; let's wait
                {
                    Thread.sleep(50);
                }
            }
            BufferedInputStream bis = new BufferedInputStream(smbFile.getInputStream());
            bis.read(bytearray, 0, bytearray.length);

            String status = "HTTP/1.1 200 OK" + CRLF; 
            LOG.DEBUG("Sending file " + filePath + " of content type " + contentType);
            ostream.write(status.getBytes(Charset.forName("UTF-8")));
            ostream.write(contentType.getBytes(Charset.forName("UTF-8")));
            ostream.write(bytearray,0,bytearray.length);
            bis.close();
            ostream.close();
            breader.close();
        }
        catch(Exception e)
        {
            new ErrorSender(e.toString(), socket, ostream).sendError();
            LOG.ERROR(e.getLocalizedMessage());
        }
    }
    
}
