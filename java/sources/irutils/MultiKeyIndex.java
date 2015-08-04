
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

/**
 * 
 */

public class MultiKeyIndex {

  RandomAccessFile postingsRaf;
  /** random access file name cache as Map */
  Map<String,RandomAccessFile> rafCache; 


  public RandomAccessFile openRandomAccessFile(String filename) {
    if (rafCache.containsKey(filename)) {
      return rafCache.get(filename);
    } else {
      RandomAccessFile raf = new RandomAccessFile(filename);
      rafCache.put(filename, raf);
      return raf;
    }
  }

  public static String sha1(String input) throws NoSuchAlgorithmException {
    MessageDigest mDigest = MessageDigest.getInstance("SHA1");
    byte[] result = mDigest.digest(input.getBytes());
    StringBuilder sb = new StringBuilder();
    
    
    for (int i = 0; i < result.length; i++) {
      sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
    }
    return sb.toString();
  }

  
  public static class Record {
    /** input line*/
    String line;
    /** line separated into fields */
    String [] fields;
    /** checksum digest of line (currently sha1) */
    String digest;
    Record(String line, String [] fields, String digest) {
      this.line = line; this.fields = fields; this.digest = digest;
    }
    String getLine() { return this.line; }
    String [] getFields() { return this.fields; }
    String getDigest() { return this.digest; }
  }


  /**
   * Load Table
   * @param tablefilename name of file containing table of records with pipe-separated fields.
   * @return list of records instances.
   * @throws FileNotFoundException
   * @throws IOException
   * @throws NoSuchAlgorithmException 
   */
  public static List<Record> loadTable(String tablefilename) 
    throws FileNotFoundException, IOException, NoSuchAlgorithmException {
    List<Record> newList = new ArrayList<Record>();
    BufferedReader br = new BufferedReader(new FileReader(tablefilename));
    String line;
    while ((line = br.readLine()) != null) {
      String[] fields = line.split("\\|");
      String digest = sha1(line);
      newList.add(new Record(line, fields, digest));
    }
    return newList;
  }

  /** container for start offset and length of a posting. */
  public static class Extent {
    long start;
    long length;
    Extent(long start, long length) { this.start = start; this.length = length; }
    long getStart() { return this.start; }
    long getLength() { return this.length; }
  }
  
 
  /**
   * Generate path for partition
   *
   * @param workingDir working directory path
   * @param indexname  name of index
   * @param columnString key column of table
   * @param termLengthString length of indexed term
   * @param suffix filename suffix
   * @return path of partition file.
   */
  public static String partitionPath(String workingDir, String indexname, 
			      String columnString, String termLengthString, String suffix) {
    return workingDir + "/indices/" + indexname + "/" + indexname + "-" + 
      columnString + "-" + termLengthString + suffix;
  }


  /**
   *  Disk based binary search implementation
   *
   * @param bsfp       file pointer for binary search table
   * @param word       search word
   * @param wordlen    wordlength
   * @param numrecs    number of records in table
   * @return long containing address of posting, -1 if not found.
   */
  public static DictionaryEntry
    dictionaryBinarySearch(RandomAccessFile bsfp, String word, 
			   int wordlen, long datalen, long numrecs)
    throws IOException
  {
    long low = 0;
    long high = numrecs;
    long cond;
    long mid;
    byte[] wordbuf = new byte[wordlen];
    String tstword;

    // System.out.println("wordlen: " + wordlen + ", datalen: " + datalen + ", numrecs: " + numrecs);
    while ( low < high )
      {
	mid = low + (high- low) / 2;
	bsfp.seek(mid * (wordlen+datalen));
	bsfp.read(wordbuf);
	tstword = new String(wordbuf);
	// System.out.println("index: " + mid + ", address: " + (mid * (wordlen+datalen)) + ", tstword: " + tstword + ", word: " + word);
	cond = word.compareTo(tstword);
	if (cond < 0) {
	  high = mid;
	} else if (cond > 0) {
	  low = mid + 1;
	} else {
	  long count = bsfp.readLong();
	  long address = bsfp.readLong();
	  return new DictionaryEntry(tstword, count, address);
	}
      }
    return null;
  }

  public static Map<String,String> readStatsFile(String filename)
    throws IOException, FileNotFoundException
  {
    Map<String,String> newMap = new HashMap<String,String>();
    BufferedReader br = new BufferedReader(new FileReader(filename));
    String line;
    while ((line = br.readLine()) != null) {
      String[] fields = line.split("\\|");
      newMap.put(fields[0], fields[1]);
    }
    br.close();
    return newMap;
  }

  public static void readPostings(RandomAccessFile extentsRaf, RandomAccessFile postingsRaf, 
			   List<String> newList, DictionaryEntry entry) 
    throws IOException
  {
    extentsRaf.seek(entry.getAddress());
    for (int i = 0; i < entry.getNumberOfPostings(); i++) {
      long offset = extentsRaf.readLong();
      long length = extentsRaf.readLong();
      byte[] buf = new byte[(int)length];
      postingsRaf.seek(offset);
      postingsRaf.read(buf);
      newList.add(new String(buf));
    }
  }
}
