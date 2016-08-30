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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;

/**
 * Stores the data.
 * In '--init' or '--update' mode, it will be populated with the metadata
 * from the actual file share, and acts as a buffer before the data is 
 * written to the database.
 * In '--daemon' mode, reads the metadata from the database, and has it ready
 * for queries from the connected clients.
 *
 */

public class DataStoreSingleton {
    
  //private static List<Document> LIST_OF_DOCS = new ArrayList<>();
  private static Map<String,Document> MAP_OF_DOCS = new LinkedHashMap<>();
  private static DataStoreSingleton instance = null;

  public static DataStoreSingleton getInstance()
  {
    if(instance==null)
    {
       instance = new DataStoreSingleton();
    }
    return instance;
  }
  
  /**
   * Returns the list of metadata documents, for example for writing to 
   * the database.
   * @return List of metadata bson documents
   */
  public List<Document> getList()
  {
      List<Document> list = new ArrayList<>();
      for (Map.Entry<String, Document> entry: MAP_OF_DOCS.entrySet() )
      {
          list.add(entry.getValue());
      }
      return list;
  }
  
    /**
   * Returns the map of metadata documents, for example for use by 
   * Speedy Gonzales.
   * @return List of metadata bson documents
   */
  public Map<String,Document> getMap()
  {
      return MAP_OF_DOCS;
  }
  

  
  /**
   * Adds a document of file metadata to the list of documents.
   * Should only be used in '--init' mode.
   * @param doc The document to be added.
   */
  public void addItem(String id, Document doc)
  {
      //LIST_OF_DOCS.add(doc);
      //LOG.DEBUG("Adding " + id.toString() + ", " + doc.toString() + "to data store");
      MAP_OF_DOCS.put(id, doc);
  }
  
  public void removeItem(String id)
  {
      MAP_OF_DOCS.remove(id);
  }
  

  /**
   * Writes the complete list of documents to the logs, when 
   * running at debug log level. 
   */
  public void printDocs() 
  {
      for (Map.Entry<String, Document> entry : MAP_OF_DOCS.entrySet())
      {
          LOG.DEBUG("Document is " + entry.getKey() + ",  " + entry.getValue().toString() + System.lineSeparator());
      }
      
  }
  
  /**
   * 
   * @return The number of documents held (e.g. files indexed).
   * Useful for troubleshooting.
   */
  public int getSize()
  {
      return MAP_OF_DOCS.size();
  }
}
