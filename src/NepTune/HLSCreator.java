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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class creates an HLS (HTTP Live Streaming) stream using FFmpeg
 * https://en.wikipedia.org/wiki/HTTP_Live_Streaming
 * https://ffmpeg.org
 * 
 */
public class HLSCreator implements Runnable {
    
    ConfigReaderSingleton properties = ConfigReaderSingleton.getInstance();
    //String tempDirectory = properties.getTempDirectory(); // Store temporary files here
    String type; // File extension
    String objectId; 
    String baseUrl; // url the client requests the stream files from
    //String playlistFilename; // url the client reloads the playlist from
    //String streamFilename; // name of individial stream file
    String sourceFile; // file to encode
    String audioCodec; // which audio codec to use (aac or mp3)
    String segment_list_type; // always m3u8 for HLS
    String segment_time; // length of stream segments
    String segment_format; // type of segment encaspulation
    String segment_list; // same as playlistFileName
    String outputFileTemplate; // what the outputfile should be called
    String ffmpegPath = properties.getProperty("ffmpegPath").trim(); 
    
    HLSCreator(String objectId, String type, String targetUrl) {
            this.objectId = objectId;
            this.sourceFile = "http://127.0.0.1:" + properties.getProperty("httpServerPort") + "?mode=file&objectId=" + objectId;
            this.type = "audio";
            this.baseUrl = targetUrl + "?mode=file&objectId=";
    }
    //TODO: Implement modes other than "audio"
    @Override
    public void run()
    {
        if ( type.equals("audio"))
        {
            audioCodec = "aac";
            segment_list_type = "m3u8";
            segment_time = "10";
            segment_format = "mpegts";
            segment_list = properties.getTempDirectory() + objectId + ".m3u8";
            outputFileTemplate = properties.getTempDirectory() + objectId + "%05d.ts";

            try {   
                convertAudio();
            } catch (IOException e) {
                LOG.ERROR("Could not convert audio file " + e.toString());
            }
        }
        else
        {
            LOG.ERROR("Speficied file type not implemented");
        }
    }
    
    private void convertAudio() throws IOException {
        List<String> command = new ArrayList<>();
        // See https://www.ffmpeg.org/ffmpeg-formats.html#hls

        command.add(ffmpegPath);
        command.add("-i"); command.add(sourceFile); //input file
        command.add("-map"); command.add("0"); // which stream to encode: https://ffmpeg.org/ffmpeg.html#Stream-selection
        command.add("-f"); command.add("segment");
        command.add("-acodec"); command.add(audioCodec);
        command.add("-segment_list_type"); command.add(segment_list_type);
        command.add("-segment_time"); command.add(segment_time);
        command.add("-segment_format"); command.add(segment_format);
        command.add("-hls_base_url"); command.add(baseUrl);
        command.add("-segment_list_entry_prefix"); command.add(baseUrl);
        command.add("-segment_list"); command.add(segment_list);
        command.add(outputFileTemplate);
        /*
        command.add(ffmpegPath);
        command.add("-i");
        command.add(sourceFile);
        command.add(segment_list);
                */
        
        command.stream().forEach((String string) -> {LOG.DEBUG(string + " ");} );
        ProcessBuilder procBuil = new ProcessBuilder(command);
        procBuil.directory(new File(properties.getTempDirectory()));
        procBuil.redirectErrorStream(true); //Write stdOut to stdErr
        Map<String, String> environ = procBuil.environment();
        Process process = procBuil.start();
        StringBuilder processOutput = new StringBuilder();
        BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        try
        {
            while (errReader.readLine() != null) 
            {
                processOutput.append(errReader.readLine());
                processOutput.append(System.lineSeparator());
            }
        } 
        catch (Exception e) 
        {
                LOG.ERROR("HLS creator " + e.toString());
        }
        try
        {
         process.waitFor();   
        }
        catch(Exception e)
        {
            LOG.ERROR("Failed to start ffmpeg " + e.toString());
        }
        LOG.DEBUG("FFMpeg output is " + System.lineSeparator() + processOutput.toString() + System.lineSeparator());
    }

} // End HLSCreator
