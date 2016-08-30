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

import java.io.InputStream;
import jcifs.smb.*;
import java.util.HashSet;

/**
 * Creates a connection to the samba share specified in 
 * /etc/nep-tune.properties.
 * https://jcifs.samba.org
 * @author toby
 */


public class SambaConnector {
    
    final String user_name;
    final String pass_word;
    final String server_address;
    final String _path;
    final String connection_string;
    final NtlmPasswordAuthentication _auth;
    HashSet<String> pathSet = new HashSet<>();
    int jj = 0;
    

    /**
     * Creates a new connection to a samba (SMB / CIFS) share.
     * Store properties in Nep-tune properties file (/etc/nep-tune.properties).
     * @param userName user name for share
     * @param passWord password for share
     * @param serverAddress address in form "smb://my.server/"
     * @param path  path in form "directory/subdirectory/"
     */
    SambaConnector(String userName, String passWord, String serverAddress, String path)
    {
        user_name = userName;
        pass_word = passWord;
        server_address = serverAddress;
        _path = serverAddress + path;
        _auth = new NtlmPasswordAuthentication(user_name + ":" + pass_word);
        connection_string = user_name + ":" + pass_word;
    }
    
    /**
     * Traverses given server / directory.
     * @return A set of strings with all files within the given
     *    server / directory, excluding subdirectories and hidden files
     *    (e.g. those prefixed with a .)
     */
    HashSet<String> populatePathSet() {
        SmbFile smbDir;
        try
        {
            smbDir = new SmbFile(_path, _auth);
            listDir(smbDir);
        }
        catch (Exception e)
        {
            LOG.ERROR("Error in sambaConnector.populatePathSet(): " + e.toString());
        }
        return pathSet;
    }
    
    
    /**
     * Does this actual traversing of the populatePathSet() method
     * @param smbDir
     * @return 
     */
    private SmbFile[] listDir(SmbFile smbDir)
    {
        SmbFile[] files = new SmbFile[0];
        try
        {   

            files = smbDir.listFiles();
            for (int ii = 0; ii < files.length; ii++)
            {
                if (files[ii].isDirectory()) // recursively parse directories
                {
                    listDir(files[ii]);
                }
                
                if ( !(files[ii].isHidden()) &&             // ignore hidden files
                      !(files[ii].isDirectory()) )          // ignore subdirectories
                {
                    String temp = files[ii].toString();
                    //LOG.DEBUG("JJ is " + jj);
                    pathSet.add(files[ii].toString());
                    LOG.DEBUG(files[ii].toString());
                    jj++;
                }
               

            }
        }
        catch (Exception e)
        {
            LOG.ERROR("Exception in sambaConnector.listDir(): " + e.toString());
        }
        return files;
    }
    
    
    /**
     * Returns an Smb input stream of a file given its full path
     * @param path path to the file (including file name)
     * @return The input stream of the file asked for
     */
    public InputStream getSambaInputStream(String path)
    {
        SmbFileInputStream istream = null;
        try
        {
            istream = new SmbFileInputStream(new SmbFile(path, _auth)); 
        }
        catch (Exception e)
        {
            LOG.ERROR("Error retrieving file in sambaConnector.getSambaInputStream() " + path + " from share: " + e.toString());
        }
        finally
        {
            try 
            {
                istream.close();
            }
            catch(Exception e)
            {
                LOG.ERROR("Error closing file in sambaConnector.getSambaInputStream() " + path + " from share: " + e.toString());
            }      
        }
        return istream;
    }
    
    /**
     * Returns a single Smb file given its full path
     * @param path path to the file (including file name)
     * @return The file asked for
     */
    public SmbFile getFile(String path)
    {
        SmbFile file = null;
        try
        {
            file = new SmbFile(path, _auth);
        }
        catch(Exception e)
        {
            LOG.ERROR("Could not get file " + path + "in SambaConnector.getFile() " + e.toString());
        }
        return file;
    }    
}
    
    


