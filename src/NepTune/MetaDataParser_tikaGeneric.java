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

import java.io.FileNotFoundException;
import java.io.IOException;
import jcifs.smb.SmbFileInputStream;
import org.bson.Document;
import static NepTune.DataParser.properties;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.LyricsHandler;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.*;

/**
 *
 * @author Toby Scholz 2016
 */
public class MetaDataParser_tikaGeneric {
    
    
    SambaConnector samba = new SambaConnector(
                properties.getProperty("sambaUser"), 
                properties.getProperty("sambaPassword"), 
                properties.getProperty("sambaServer"),
                properties.getProperty("sambaPath"));
    
    MetaDataParser_tikaGeneric()
    {
        
    }
    
    
     /**
     * Utilises Apache Tika
     * Inspects and mp3 file for ID3 metadata, and adds that metadata 
     * to the supplied bson document.
     * @param path Path to the file to be inspected
     * @param document Document for mp3 file to be parsed (should already 
     * contain directory, file name, and type from splitPath().
     * @return The updated document with added metadata
     */
    Document decodeFile(String path, Document document)
    {
        
      Document doc = document;
      SmbFileInputStream inputstream;
      try 
      {
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        inputstream = new SmbFileInputStream(samba.getFile(path));
        ParseContext pcontext = new ParseContext();
        LOG.INFO("Parsing: " + path);
        parser.parse(inputstream, handler, metadata, pcontext);

        LyricsHandler lyrics = new LyricsHandler(inputstream,handler);
        StringBuilder lyrs = new StringBuilder();
        while(lyrics.hasLyrics()) {
            lyrs.append(lyrics.toString());
        }
        if (lyrs != null && lyrs.length() > 0 )
        {
            doc.append("lyrics", lyrs.toString());
        }
        String[] metadataNames = metadata.names();

        for(String name : metadataNames) {		
            doc.append(name, metadata.get(name));
        }
        inputstream.close();
      }
      catch(FileNotFoundException e)
      {
          LOG.ERROR("File for found for decondig: " + path + ", " + e.toString());
      }
      catch(IOException e)
      {
          LOG.ERROR("IO error for " + path + ", " + e.toString());
      }
      catch(SAXException e)
      {
          LOG.ERROR("XML parsing exception for " + path + ", " + e.toString());
      }
      catch(TikaException e)
      {
          LOG.ERROR("Tika exception for " + path + ", " + e.toString());
      }
      return doc;
    }
} // end class

