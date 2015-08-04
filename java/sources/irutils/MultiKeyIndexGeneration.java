
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
import irutils.MultiKeyIndex.Extent;

/**
 * 
 */

public class MultiKeyIndexGeneration {
  /*
    Memory based temporary inverted file
    two tables;
    <ol>
    <li>digest -> postings record map
    <li>column -> termlength -> term -> digest map 
    </ol>
  */

  /** start a new digest list for term using map from column -> termLength 
   * @param newMap string -> digest -list map
   * @param term   term to be indexed
   * @param digest digest of postings for term
   */
  public static void addNewDigestList(Map<String,List<String>> newMap, String term, String digest) {
    List<String> newList = new ArrayList<String>();
    newList.add(digest);
    newMap.put(term, newList);
  }

  /** column -> termlength -> term -> digest map */
  Map<Integer,Map<Integer,Map<String,List<String>>>> columnLengthTermDigestMap;
  /** digest -> single posting map */
  Map<String,String> digestPostingMap;

  /**
   * Generate in-memory term dictionary
   * @param recordTable list of record instances
   * @param columns  which columns of records to use as keys.
   */
  public void generateMaps(List<Record> recordTable, int[] columns) {
    // create in-memory representation of file maps
    this.columnLengthTermDigestMap =  new HashMap<Integer,Map<Integer,Map<String,List<String>>>>();
    this.digestPostingMap = new HashMap<String,String>();

    for (int column: columns) {
      this.columnLengthTermDigestMap.put(column, new HashMap<Integer,Map<String,List<String>>>());
    }
    for (Record record: recordTable) {
      String[] fields = record.getFields();
      String digest = record.getDigest();
      this.digestPostingMap.put(digest, record.getLine()); // store hash -> postings
      for (int column: columns) {
	String term = fields[column].toLowerCase();
	if (this.columnLengthTermDigestMap.get(column).containsKey(term.length())) {
	  if (this.columnLengthTermDigestMap.get(column).get(term.length()).containsKey(term)) {
	    // store column -> term -> hash list
	    this.columnLengthTermDigestMap.get(column).get(term.length()).get(term).add(digest);
	  } else {
	    Map<String,List<String>> termDigestMap = this.columnLengthTermDigestMap.get(column).get(new Integer(term.length()));
            List<String> newList = new ArrayList<String>();
            newList.add(digest);    
            termDigestMap.put(term, newList);
	  }
	} else {
	  Map<String,List<String>> newTermDigestMap = new TreeMap<String,List<String>>();
	  List<String> newList = new ArrayList<String>();
          newList.add(digest);    
          newTermDigestMap.put(term, newList);
          this.columnLengthTermDigestMap.get(column).put(term.length(), newTermDigestMap);
	}
      }
    }
  }


  /**
   * Write postings to posting pool file while filling digest -> posting extent map that is returned at end of processing.
   * @param workingdir working directory
   * @return map of string -> start, offset pairs (extents)
   * @throws IOException 
   */
  public Map<String, Extent> writePostings(String workingdir, String indexname) 
    throws IOException { 
    Map<String, Extent> digestExtentMap = new TreeMap<String, Extent>();
    RandomAccessFile raf = new RandomAccessFile(workingdir + "/indices/" + indexname + "/postings", "rw");
    for (Map.Entry<String,String> digestEntry: this.digestPostingMap.entrySet()) {
      byte[] byteData = digestEntry.getValue().getBytes(); // convert posting string to bytes
      long start = raf.getFilePointer();
      raf.write(byteData);
      long end = raf.getFilePointer();
      if ((end - start) != byteData.length) {
	System.out.println("Warning: extent: (" + end + " - " + start + ") = " + (end - start) +
			   " does not equal byteData length: " + Integer.toString(byteData.length));
      }
      digestExtentMap.put(digestEntry.getKey(), new Extent(start, byteData.length));
    }
    return digestExtentMap;
  }

  /**
   * Using column length term digest map and digest extent Map, create
   * partitions consisting of two files: a dictionary containing term,
   * num-of-postings, and pointer to extent list and extext list pool
   * containing offset length pairs, one for each posting.
   *
   * @param workingDir working directory
   * @param indexname name of index
   * @param digestExtentMap map of digest -> start length pairs (extents)
   * @throws IOException
   * @throws FileNotFoundException
   * @throws NoSuchAlgorithmException
   */
  public void writePartitions(String workingDir, String indexname, Map<String, Extent> digestExtentMap) 
    throws FileNotFoundException, IOException
  {
    for (Integer column: this.columnLengthTermDigestMap.keySet()) {
      for (Integer termLength: this.columnLengthTermDigestMap.get(column).keySet()) {
	RandomAccessFile termDictionaryRaf = 
	  new RandomAccessFile(MultiKeyIndex.partitionPath(workingDir, indexname,
					     column.toString(), termLength.toString(), "-term-dictionary"), "rw");
	RandomAccessFile extentsRaf = 
	  new RandomAccessFile(MultiKeyIndex.partitionPath(workingDir, indexname,
					     column.toString(), termLength.toString(), "-postings-offsets"), "rw");
	int recordnumber = this.columnLengthTermDigestMap.get(column).get(termLength).size();
	long datalength = 16;
	long recordlength = termLength.intValue() + datalength;
	for (Entry<String,List<String>> termEntry: this.columnLengthTermDigestMap.get(column).get(termLength).entrySet()) {
	  byte[] byteData = termEntry.getKey().getBytes();
	  List<String> digestList = termEntry.getValue();
	  long extentListOffset = extentsRaf.getFilePointer();
	  // write extents
	  for (String digest: digestList) {
	    Extent extent = digestExtentMap.get(digest);
	    extentsRaf.writeLong(extent.getStart());
	    extentsRaf.writeLong(extent.getLength());
	  }
	  // write dictionary
	  
	  long dictEntryStart = termDictionaryRaf.getFilePointer();
	  termDictionaryRaf.write(byteData);		  // term
	  
	  long dictEntryDataStart = termDictionaryRaf.getFilePointer();
	  termDictionaryRaf.writeLong(digestList.size()); // number of postings
	  termDictionaryRaf.writeLong(extentListOffset);  // offset to begining of extent list
	  datalength = termDictionaryRaf.getFilePointer() - dictEntryDataStart;
	  recordlength = termDictionaryRaf.getFilePointer() - dictEntryStart;
	}
	termDictionaryRaf.close();
	extentsRaf.close();
	BufferedWriter bw =
	  new BufferedWriter
	  (new FileWriter
	   (workingDir + "/indices/" + indexname + "/" + indexname + "-" + 
	    column.toString() + "-" + termLength.toString() + "-term-dictionary-stats.txt"));
	bw.write("termlength|" + termLength + "\n");
	bw.write("reclength|"  + recordlength + "\n");
	bw.write("datalength|" + datalength + "\n");
	bw.write("recordnum|"  + recordnumber + "\n");
	bw.close();
      }
    }
  }


  public List<String> lookup(String workingDir, String indexname, int column, String term)
    throws IOException, FileNotFoundException
  {
    List<String> resultList = new ArrayList<String>();
    String termLengthString = Integer.toString(term.length());
    String columnString = Integer.toString(column);
      RandomAccessFile termDictionaryRaf = 
	new RandomAccessFile(MultiKeyIndex.partitionPath(workingDir, indexname,
					   columnString, termLengthString, "-term-dictionary"), "r");
    RandomAccessFile extentsRaf = 
      new RandomAccessFile(MultiKeyIndex.partitionPath(workingDir, indexname,
					 columnString, termLengthString, "-postings-offsets"), "r");
    RandomAccessFile postingsRaf = 
      new RandomAccessFile(workingDir + "/indices/" + indexname + "/postings", "r");

    Map<String,String> statsMap =
      MultiKeyIndex.readStatsFile(MultiKeyIndex.partitionPath
		    (workingDir, indexname,
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
      System.out.println("option: " + option);
      System.out.println("workingDir: " + workingDir);
      System.out.println("indexname: " + indexName);
      System.out.println("column: " + column);

      Map<String,String []> tableConfig = Config.loadConfig(workingDir + "/tables/ifconfig");
      String[] tableFields = tableConfig.get(indexName);
      if (option.equals("build")) {
	if (tableFields != null) {
	  String tableFilename = tableFields[0];
	  // get specified columns from table entry
	  String[] columnStrings = tableFields[3].split(",");
	  int columns[] = new int[columnStrings.length];
	  for (int i = 0; i < columnStrings.length; i++) {
	    columns[i] = Integer.parseInt(columnStrings[i]);
	  }
	  System.out.println("loading table for " + indexName + " from file: " + tableFilename + ".");
	  List<MultiKeyIndex.Record> recordTable = MultiKeyIndex.loadTable(workingDir + "/tables/" + tableFilename);
	  MultiKeyIndexGeneration instance = new MultiKeyIndexGeneration();
	  System.out.println("Generating maps for columns " + columns ); 
	  instance.generateMaps(recordTable, columns);
	  Map<String,Extent> digestExtentMap = instance.writePostings(workingDir, indexName);
	  instance.writePartitions(workingDir, indexName, digestExtentMap);
	} else {
	  System.out.println("table entry for index " + indexName + " is not present in configuration file: ifconfig.");
	}
      } else if (option.equals("lookup")) {
	StringBuilder termBuf = new StringBuilder();
	for (int i = 4; i < args.length; i++) {
	  termBuf.append(args[i]).append(" ");
	}
	String term = termBuf.toString().trim();
	System.out.println("term: " + term);
	MultiKeyIndexGeneration instance = new MultiKeyIndexGeneration();
	List<String> resultList = instance.lookup(workingDir, indexName, Integer.parseInt(column), term);
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
