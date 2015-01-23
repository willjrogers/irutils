package irutils;

import utils.StringUtils;
import java.util.*;
import java.io.*;

/**
 * InspectIF.java
 *
 *
 * Created: Mon Dec  2 16:27:43 2002
 *
 * @author <a href="mailto:wrogers@nls10.nlm.nih.gov">Willie Rogers</a>
 * @version 0.01
 */

public class InspectIF {

  interface DictionaryEntryHandler {
    void handle(InvertedFile index, String key, int count, int address);
  }

  static class VerboseOutputDictionaryEntryHandler implements DictionaryEntryHandler {
    public void handle(InvertedFile index, String key, int count, int address)
    {
      System.out.println("key: " + key + ", postings count: " + count + ", postings address: " + address );
    }
  }

  static class PipedOutputDictionaryEntryHandler implements DictionaryEntryHandler {
    public void handle(InvertedFile index, String key, int count, int address)
    {
      System.out.println(key + "|" + count + "|" + address );
    }
  }

  static class PostingsOutputDictionaryEntryHandler implements DictionaryEntryHandler {
    public void handle(InvertedFile index, String key, int count, int address)
    {
      System.out.println(key + "|" + count + "|" + address );
    }
  }

  /** maximum term key length */
  public static final int MAXKEYLENGTH = 256; // this is probably too short

  public static DictionaryEntryHandler verboseEntryHandler = new VerboseOutputDictionaryEntryHandler();
  public static DictionaryEntryHandler pipedEntryHandler = new PipedOutputDictionaryEntryHandler();
  public static DictionaryEntryHandler postingsEntryHandler = new PostingsOutputDictionaryEntryHandler();

  /**
   * Creates a new <code>InspectIF</code> instance.
   *
   */
  public InspectIF (){

  }


  /**
   * List number of partitions in inverted file.
   *
   * @param index an <code>InvertedFile</code> value
   */
  public static void listPartitions(InvertedFile index)
  {
    Iterator iterator = index.numrecs.keySet().iterator();
    while (iterator.hasNext()) {
      System.out.println("numrecs key: " + iterator.next());
    }
  }


  /**
   * Inspect dictionary structure.
   *
   * @param dictionaryFile file containing dictionary, a <code>RandomAccessFile</code> value
   * @param keylength dictionary key length, an <code>int</code> value
   * @param numrecs number of records in dictionarym], an <code>int</code> value
   * @exception java.io.IOException if an error occurs
   */
  public static void inspectDictionaryEntries(DictionaryEntryHandler entryHandler,
					      InvertedFile index, 
					      RandomAccessFile dictionaryFile, 
					      int keylength, int numrecs)
    throws java.io.IOException
  {
    byte[] keybuf = new byte[keylength];
    for (int i = 0; i < numrecs; i++) {
      dictionaryFile.read(keybuf);
      int count = dictionaryFile.readInt();
      int address = dictionaryFile.readInt();
      String key = new String(keybuf);
      entryHandler.handle(index, key, count, address);
    }
  }

  /**
   * Inspect Inverted File structure.
   *
   * @param index an <code>InvertedFile</code> value
   * @exception java.io.FileNotFoundException if an error occurs
   * @exception java.io.IOException if an error occurs
   */
  public static void inspectInvertedFile(InvertedFile index, boolean verbose)
     throws java.io.FileNotFoundException, java.io.IOException
  {
    RandomAccessFile dictionaryFile;
    List postings;
    int num_of_partitions = 0; 
    for (int keyLength = 0; keyLength < MAXKEYLENGTH; keyLength++) {
      String partitionId = index.indexname + keyLength;
      String partitionFilename = index.indexParentDirectoryPath + File.separator +
	index.indexname + File.separator + "partition_" + partitionId;
      File partitionFile = new File(partitionFilename);
      if (partitionFile.exists()) {
	dictionaryFile = new RandomAccessFile ( partitionFile, "r" );
	System.out.println("key length: " + keyLength);
	num_of_partitions++;
	DictionaryEntryHandler entryHandler = null;
	if (verbose) {
	  entryHandler = verboseEntryHandler;
	} else {
	  entryHandler = pipedEntryHandler;
	}
	  inspectDictionaryEntries
	    (entryHandler, index, dictionaryFile, keyLength,
	     ((Integer)index.numrecs.get(partitionId)).intValue());

      }
    }
  }


  
  public static void listEntriesForPostingsLargerThan 
    (DictionaryEntryHandler entryHandler, 
     InvertedFile index, 
     int keylength, String partitionId, 
     int numrecs, int minPostingsLength)
    throws java.io.IOException
  {
    RandomAccessFile dictionaryFile;
    String partitionFilename = index.indexParentDirectoryPath + File.separator +
      index.indexname + File.separator + "partition_" + partitionId;
    File partitionFile = new File(partitionFilename);
    if (partitionFile.exists()) {
      dictionaryFile = new RandomAccessFile ( partitionFile, "r" );
      
      byte[] keybuf = new byte[keylength];
      for (int i = 0; i < numrecs; i++) {
	dictionaryFile.read(keybuf);
	int count = dictionaryFile.readInt();
	int address = dictionaryFile.readInt();
	String key = new String(keybuf);
	if ( count >= minPostingsLength)
	  entryHandler.handle(index, key, count, address);
      }
      dictionaryFile.close();
    }
  }

  public static void listPostingsLargerThan(InvertedFile index, int minPostingsLength,
					    boolean verbose, boolean inspectRecords)
    throws java.io.FileNotFoundException, java.io.IOException
  {
    DictionaryEntryHandler entryHandler = verboseEntryHandler;
    List postings;
    int num_of_partitions = 0; 
    if ( inspectRecords ) {
      entryHandler = postingsEntryHandler;
    }
    for (int keyLength = 0; keyLength < MAXKEYLENGTH; keyLength++) {
      System.out.println("key length: " + keyLength);
      String partitionId = index.indexname + keyLength;
      if ( index.numrecs.containsKey(partitionId) )
	{
	  num_of_partitions++;
	  listEntriesForPostingsLargerThan 
	    ( entryHandler, index, keyLength, partitionId,
	      ((Integer)index.numrecs.get(partitionId)).intValue(), minPostingsLength );
	}
    }
  }

  public static void printHelp()
  {
    System.out.println();
    System.out.println("Commands:");
    System.out.println("  inspect               - inspect inverted file completely");
    System.out.println("  listpostings minsize  - list postings >= size");
    System.out.println("  listpostings2 minsize - list postings >= size and inspect record lengths");
    System.out.println();
  }

  public static void interactive(InvertedFile index)
    throws java.io.FileNotFoundException, java.io.IOException
  {
   

    System.out.println("Current partitions: ");
    listPartitions(index);
    printHelp();
    BufferedReader terminalIn = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("InspectIF> ");
    String line = terminalIn.readLine();
    while (line.length() > 0) {
      List tokens = StringUtils.split(line, " ");
      Iterator tokenIterator = tokens.iterator();
      String token = (String)tokenIterator.next();
      if (token.equals("inspect")) {
	inspectInvertedFile(index, false);
      } else if (token.equals("listpostings")) {
	token = (String)tokenIterator.next();
	listPostingsLargerThan(index, Integer.parseInt(token), false, false);
      } else if (token.equals("listpostings2")) {
	token = (String)tokenIterator.next();
	listPostingsLargerThan(index, Integer.parseInt(token), false, true);
      } else if (token.equals("help")) {
	printHelp();
      }
      System.out.print("InspectIF> ");
      line = terminalIn.readLine();
    }
    terminalIn.close();
  }
  
  /**
   * main program 
   * @param args argument vector.
   *
   * usage: irutils.IFQuery <indexname> <queryterm> [| <queryterm>]
   * properties:
   *   index.path=<directory path> : where path of indices resides
   *   table.path=<directory path> : where tables reside
   * @exception java.io.FileNotFoundException if an error occurs
   * @exception java.io.IOException if an error occurs
   * @exception BSPIndexCreateException if an error occurs
   * @exception BSPIndexInvalidException if an error occurs
   * @exception ClassNotFoundException if an error occurs
   */
  public static void main(String[] args)
    throws java.io.FileNotFoundException,
    java.io.IOException, BSPIndexCreateException, BSPIndexInvalidException, 
    ClassNotFoundException
  {

    String words[] = { };	// empty array
    String indexPath = 
      System.getProperty("index.path", 
			 "/home/wrogers/devel/exper/irutils/java/indices");
    String tablePath =
      System.getProperty("table.path",
			 "/home/wrogers/devel/exper/irutils/java/tables");

    if (args.length < 1) {
      System.out.println("usage: irutils.IFQuery <indexname>");
      System.out.println("properties: ");
      System.out.println("  -Dindex.path=<directory path> : where path indices resides");
      System.out.println("  -Dtable.path=<directory path> : where tables reside");
      System.exit(0);
    }

    String indexname = args[0];

    // open a container to keep track of the indices       
    InvertedFileContainer container = 
      new InvertedFileContainer(tablePath, indexPath);

    // get a index instance for "recommendations"
    InvertedFile index = container.get(indexname);
    if (index == null)
      {
	System.err.println("error creating index for " + indexname);
	System.err.println("missing entry in config file: ifconfig for " + 
			   indexname + ".");
	System.exit(1);
      }
    if (IFQuery.verbose)
      System.out.println("using index: " + index);


    // check to see if index exists, if not then create it. 
    index.update();

    // setup index for retrieval.
    index.setup();

    interactive(index);

    index.release();
  }



  
}// InspectIF
