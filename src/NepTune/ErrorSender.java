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

import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.charset.Charset;

/**
 * Compiles an html error page for further processing by the client
 * Note the html status is OK (200).
 */
public class ErrorSender {
    
    DataOutputStream ostream = null;
    String reason = null;
    Socket socket;
    private final static String CRLF = "\r\n";
    private static final String CHARSET = java.nio.charset.StandardCharsets.UTF_8.name();
    
    /**
     * Creates an error html message for the client
     * @param reason: What went wrong (user-friendly)
     * @param socket: Where to send it to
     * @param ostream: Stream that was previously created on the socket
     */
    ErrorSender(String reason, Socket socket, DataOutputStream ostream)
    {
        this.ostream = ostream;
        this.reason = reason;
        this.socket = socket;
    }
    
    /**
     * Sends the error with the details supplied in the constructor
     */
    public void sendError()
    {
        String status = "HTTP/1.1 200 OK" + CRLF; // It's a cheat - Apple's iOS doesn't like it when status is not ok.
        String contentType = "Content-type: " + "text/html; charset=UTF-8" + CRLF;//content info
        String encoding = "charset: " + CHARSET + CRLF;
        String body = "<!DOCTYPE html>\n<html>" +
             "<head><title>Error</title></head>" +
             "<body>" + reason + "</body></html>";
        try
        {
            ostream.write(status.getBytes(Charset.forName(CHARSET)));
            ostream.write(contentType.getBytes(Charset.forName(CHARSET)));
            ostream.write(encoding.getBytes(Charset.forName(CHARSET)));
            ostream.write(CRLF.getBytes(Charset.forName(CHARSET))); // end of header
            ostream.write(body.getBytes(Charset.forName(CHARSET)));
            ostream.close();
            socket.close();  
        }
        catch(Exception e)
        {
            LOG.ERROR("Failed to write to socket " + socket.getInetAddress());
        }
    }
}
