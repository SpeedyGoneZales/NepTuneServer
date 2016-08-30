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
import java.util.List;
import java.util.Map;
import org.bson.Document;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Implementation of multi-threaded search
 */
class SpeedyGonzales extends Thread {
    
    List<Map<String,String>> fastDocList = new ArrayList<>(); // The 'fast' list stores the document as string, making it faster to search
    DataStoreSingleton ds = DataStoreSingleton.getInstance(); 
    Map<String,Document> slowDocMap = ds.getMap(); //Make a copy (who knows what Java does?!?) of the document store, so we can have multiple instances without access violations)
    
    int numberOfProcessorCores = Runtime.getRuntime().availableProcessors(); // how many threads can we sensibly have?
    int sectionDivisor = ( Math.round(slowDocMap.size() / numberOfProcessorCores ));
    
    
    
    SpeedyGonzales() {
        super();
        splitData();
        LOG.DEBUG("Number of Processor cores found is " + numberOfProcessorCores);
        LOG.DEBUG("Number of records is " + ds.getSize()); 
        LOG.DEBUG("SectionDivisor is " + sectionDivisor);
    }
    
   
    /** 
     * 
     * @param searchTerms: the terms we want to find in the collection
     * @return: List of JSON objects that contain our search terms
     * @throws InterruptedException
     * @throws ExecutionException 
     */
    public List<Document> findString(List<String> searchTerms) throws InterruptedException, ExecutionException 
    {
        List<Document> result = new ArrayList<>();
        List<List<Document>> searchResult = new ArrayList<>(); //List of documents that holds all the results. Not sure if threadsafe atm.
        ExecutorService executor = Executors.newFixedThreadPool(fastDocList.size()); // New thread pool the size of entries in the list, which is the same as the number of processors.
        List<Future<List<Document>>> threadList = new ArrayList<>(); // For each thread we have a list entry, which itself is a list of documents.
        
        for ( int ii = 0; ii < fastDocList.size(); ii++ )
        {
        final int jj = ii;
        threadList.add( executor.submit(new Callable<List<Document>>() {
            @Override
            public List<Document> call() throws Exception {
                
            List<Document> resultList = new ArrayList<>();
                
                try {
                    LOG.DEBUG("Starting thread no " + jj);
                    Map<String,String> quickMap = fastDocList.get(jj);
                    for (Map.Entry<String,String> entry : quickMap.entrySet() )
                    {
                        boolean found = false;
                        for ( String substring : searchTerms )
                        {
                        if ( entry.getValue().toLowerCase().contains(substring.toLowerCase()) )
                            {
                                resultList.add(slowDocMap.get(entry.getKey()));
                                LOG.DEBUG("Run: " + jj + " Added " + entry.getKey() + " to list" );
                                found = true;
                                break;
                            }  
                        }
                        if (found)
                        {
                            break;
                        }
                    }
                }
                catch(Exception e){
                    LOG.ERROR("Error in the Multithreaded search logic: " + e.toString());
                    throw new UnsupportedOperationException("Not supported yet."); 
                }
                LOG.DEBUG("Thread no " + jj + " completed");
                return resultList;
            } // end call()
        }) //end executor.submit()  
        ); // end threadList.add
        } // end for loop
        executor.shutdown(); // Should block until all threads have completed.
        
        for (Future<List<Document>> entry : threadList)
        {
            result.addAll(entry.get());
        }
        
        for (List<Document> entry : searchResult)
        {
            result.addAll(entry);
        }
        LOG.DEBUG("Result contains " + result.size() + " entries");
         
        return result;
        
    }
    
    /**
     * split the data into roughly equal chunks for multi-threaded 
     * searching
     */
    private void splitData() {
        
        int ii = 1;
        int multiplier = 1;
        
        Map<String, String> temp = new HashMap<>();
        for ( Map.Entry<String, Document> entry : slowDocMap.entrySet() )
        {
            temp.put( entry.getKey(), entry.getValue().toString() );

            if ( ii >= sectionDivisor * multiplier || ii == slowDocMap.size() )
            {
                fastDocList.add( (multiplier -1), new HashMap<>(temp) );
                multiplier++;
                temp.clear();
            }
            ii++;
        }
    } // end splitData
} // end class
