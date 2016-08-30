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

/**
 * Creates hashes to make it quicker to check if two files are the same
 * Should not be necessary to touch this class normally.
 */

import java.math.BigInteger;
import java.security.MessageDigest;
import jcifs.smb.SmbFile;
        
class FileHasher {
    
    FileHasher()
    {
    }
    
    String generateHash(SmbFile file)
    {
        return generateHash(file.getCanonicalPath() + String.valueOf(file.getContentLengthLong()) + String.valueOf(file.getLastModified()) );
    }
    
    String generateHash(String input)
    {
        String hash = null;
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes(), 0, input.length());
            hash = new BigInteger(1, md.digest()).toString(16);
        }
        catch(Exception e)
        {
            LOG.ERROR("Error hashing input " + input + " " + e.toString());
        }
        return hash;
    }  
}
