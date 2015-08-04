
//
package irutils;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.util.TreeMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import irutils.MultiKeyIndex.Record;

/**
 * 
 */

public class MultiKeyIndexLookup {

  String workingDir;

  MultiKeyIndexLookup(String workingdir) {
    this.workingDir = workingdir;
  }

  public List<String> lookup(String indexname, int column, String term)
    throws IOException, FileNotFoundException
  {
    List<String> resultList = new ArrayList<String>();
    String termLengthString = Integer.toString(term.length());
    String columnString = Integer.toString(column);
      RandomAccessFile termDictionaryRaf = 
	new RandomAccessFile(MultiKeyIndex.partitionPath(this.workingDir, indexname,
					   columnString, termLengthString, "-term-dictionary"), "r");
    RandomAccessFile extentsRaf = 
      new RandomAccessFile(MultiKeyIndex.partitionPath(this.workingDir, indexname,
					 columnString, termLengthString, "-postings-offsets"), "r");
    RandomAccessFile postingsRaf = 
      new RandomAccessFile(this.workingDir + "/indices/" + indexname + "/postings", "r");

    Map<String,String> statsMap =
      MultiKeyIndex.readStatsFile(MultiKeyIndex.partitionPath
				  (this.workingDir, indexname,
				   columnString, termLengthString, "-term-dictionary-stats.txt"));

    int datalength = Integer.parseInt(statsMap.get("datalength"));
    int recordnum = Integer.parseInt(statsMap.get("recordnum"));
    
    DictionaryEntry entry = 
      MultiKeyIndex.dictionaryBinarySearch(termDictionaryRaf, term, 
			     term.length(), datalength, recordnum );
    if (entry != null) {
      resultList.add(entry.toString());
      MultiKeyIndex.readPostings(extentsRaf, postingsRaf, resultList, entry);
    } else {
      resultList.add("\"" + term + "\" entry is " + entry);
    }
    termDictionaryRaf.close();
    extentsRaf.close();
    postingsRaf.close();
    return resultList;
  }

  /**
   * The main program
   * @param args Arguments passed from the command line
   * @throws IOException
   * @throws FileNotFoundException
   * @throws NoSuchAlgorithmException
   **/
  public static void main(String[] args)
    throws FileNotFoundException, IOException, NoSuchAlgorithmException
  {
    if (args.length > 4) {
      String option = args[0];
      String workingDir = args[1];
      String indexName = args[2];
      String column = args[3];

      Map<String,String []> tableConfig = Config.loadConfig(workingDir + "/tables/ifconfig");
      String[] tableFields = tableConfig.get(indexName);
      if (option.equals("lookup")) {
	StringBuilder termBuf = new StringBuilder();
	for (int i = 4; i < args.length; i++) {
	  termBuf.append(args[i]).append(" ");
	}
	String term = termBuf.toString().trim();
	MultiKeyIndexLookup instance = new MultiKeyIndexLookup(workingDir);
	System.out.println("option: " + option);
	System.out.println("workingDir: " + workingDir);
	System.out.println("indexname: " + indexName);
	System.out.println("column: " + column);
	System.out.println("term: " + term);
	List<String> resultList = instance.lookup(indexName, Integer.parseInt(column), term);
	for (String result: resultList) {
	  System.out.println(result);
	}
	
      } else {
	System.out.println("Unknown option.");
	System.out.println("Usage: build workingdir indexname");
	System.out.println("       lookup workingdir indexname term");
      }
    }
  }
}
