package utils;

import java.util.*;
import java.io.*;

/**
 * Implementation of Binary Search Partition Inverted File.
 * <p>
 * Example of use:
 * <pre>
 *   String indexRoot = "/home/wrogers/devel/exper/irutils/java/indices";
 *   String tableRoot = "/home/wrogers/devel/exper/irutils/java/tables";
 *   String indexname = "mrcon";
 *   // open a container to keep track of the indices       
 *   BSPContainer bspContainer = new BSPContainer(tableRoot, indexRoot);
 *
 *   // check to see if index exists, if not then create it. 
 *   bspIndex.update();
 *
 *   // setup index for retrieval.
 *   bspIndex.setup();
 *   
 *   BSPTuple result = bspIndex.lookup("C00001403");
 *   ArrayList list = (ArrayList)result.getValue();
 *   for (Iterator j = list.iterator(); j.hasNext(); ) {
 *     System.out.println(j.next());
 *   }
 * </pre>
 * Resulting output:
 * <pre>
 *  C0001403|ENG|P|L0001403|PF|S0010794|Addison's Disease|0
 *  C0001403|ENG|P|L0001403|VC|S0352253|ADDISON'S DISEASE|0
 *  C0001403|ENG|P|L0001403|VC|S0354372|Addison's disease|0
 *  C0001403|ENG|P|L0001403|VO|S0010792|Addison Disease|0
 *  C0001403|ENG|P|L0001403|VO|S0010796|Addisons Disease|0
 *  C0001403|ENG|P|L0001403|VO|S0033587|Disease, Addison|0
 *  C0001403|ENG|P|L0001403|VO|S0352252|ADDISON DISEASE|0
 *  C0001403|ENG|P|L0001403|VO|S0469271|Addison's disease, NOS|3
 *  C0001403|ENG|P|L0001403|VO|S1911394|Disease;Addisons|3
 *   ... output truncated ...
 * </pre>
 * ifconfig for this table (see utils.InvertedFileContainer):
 * <pre>
 * NUM_TABLES: 1
 * MRCON|mrcon|8|0|cui|lat|ts|lui|stt|sui|str|lrl|TXT|TXT|TXT|TXT|TXT|TXT|TXT|TXT
 * </pre>
 * </p>
 * Created: Fri Jul  6 15:37:53 2001
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: InvertedFile.java,v 1.1 2001/08/31 19:21:53 wrogers Exp $
 * @see utils.InvertedFileContainer
 */

public class InvertedFile implements Serializable
{
  /** list of supportted binary formats */
  static HashMap binFormats = new HashMap(4);
  /** do this once at class instantiation */
  static
   {
     binFormats.put("INT", "d1");
     binFormats.put("TXT", "a");
     binFormats.put("PTR", "i1");
   }

  /** hashlist of hash or tree maps for generating new indices. */
  transient HashMap hashlist= new HashMap(5);

  /** hashmap of open partition files. */
  transient HashMap partitionFiles = new HashMap(5);

  /** postings file */
  transient RandomAccessFile postingsFile;

  /** Is current index valid? */
  boolean valid = false;

  /** name of index */
  String indexname;

  /** name of table index is derived from */
  String tablefilename;

  /** directory path of parent directory for index */
  String indexParentDirectoryPath;

  /** format for data */
  ArrayList indexFormat;
  
  /** number of words in index */
  int wordnum;

  /** number of records in each partition */
  HashMap numrecs;

  /** datalen in dictionary part of each partition */
  HashMap dataLength;

  /** list of key indices (Integer)  used for this index. (default is [0]) */
  ArrayList keyIndices = null;		// if null, key index is zero

  /** default constructor for serialization purposes only. */
  public InvertedFile()
  {
    // System.out.println("InvertedFile: instantiation");
  }

  /** Constructor 
   * @param indexname      name of index
   * @param tablefilename   filename containing source table data
   * @param indexParentDir parent directory of index
   * @param format         table data format
   */
  public InvertedFile(String indexname, String tablefilename, 
		  String indexParentDir, ArrayList format)
  {
    this.indexname = indexname;
    this.tablefilename = tablefilename;
    this.indexParentDirectoryPath = indexParentDir;
    this.indexFormat = format;
    ArrayList keyList = utils.StringUtils.split((String)format.get(3), ",");
    // convert key indices to Integer.
    this.keyIndices = new ArrayList();
    for (int i = 0; i < keyList.size(); i++) {
      keyIndices.add(new Integer((String)keyList.get(i)));
    }
  }

  /** load table into in-memory term -> value map.
      Column zero is used as key for index. */
  public void load_map()
     throws FileNotFoundException, IOException
  {
    // Load records into buckets based on term length.  Each bucket is
    // a TreeMap where record is stored by the ordinal value of the
    // first element (key) of the record.
    String line;
    String key = null;
    ArrayList lineList;
    int i = 0;
    // System.out.println("loading map " + this.indexname );
    BufferedReader reader = 
      new BufferedReader(new FileReader( this.tablefilename ));
    while ( (line = reader.readLine()) != null )
      {
	Map bucket;
	i++;
	if (line.trim().length() > 0) {
	  lineList = StringUtils.split(line, "|");
	  if (lineList.size() > 0) {
	    if (this.keyIndices == null || 
		(this.keyIndices.size() == 1 && 
		 ((Integer)this.keyIndices.get(0)).intValue() == 0)) {
	      key = (String)lineList.get(0);
	    } else {
	      StringBuffer strBuf = new StringBuffer();
	      for (int j = 0; j < keyIndices.size(); j++) {
		int col = ((Integer)keyIndices.get(j)).intValue();
		strBuf.append((String)lineList.get(col));
	      }
	      key = strBuf.toString();
	    }
	  } else {
	    System.err.println("lineList size <= 0, line = " + line);
	  }
	}
	String keyLength = new Integer (key.length()).toString();
	bucket = (Map)this.hashlist.get(this.indexname+keyLength);
	if (bucket == null ) {
	  bucket = new TreeMap();
	  this.hashlist.put(this.indexname+keyLength, bucket);
	  ArrayList postings = new ArrayList();
	  postings.add(line);
	  bucket.put(key, postings);
	} else {
	if ( bucket.containsKey(key) )
	  {
	    ArrayList postings = (ArrayList)bucket.get(key);
	    postings.add(line);
	  }
	else
	  {
	    ArrayList postings = new ArrayList();
	    postings.add(line);
	    bucket.put(key, postings);
	  }
	}
	wordnum++;
	// System.out.println("bucket.put(key: " + key + ", value), " + 
	// "keylength: " + keyLength + ", bucket size: " + bucket.size());
      }
    reader.close();
    // System.out.println("# of input lines: " + i );
    // System.out.println("# of buckets: " + hashlist.size());
    Iterator iter = hashlist.values().iterator();
    while (iter.hasNext()) {
      Map bucket = (Map)iter.next();
      // System.out.println("bucket, size: " + bucket.size());
    }
  }

  /**
   * Generate disk-based word map from existing map in memory
   * @exception InvertedFileCreateException occurs if index cannot be created.
   */
  public void create()
    throws BSPIndexCreateException, IOException
  {
    RunLengthPostingsWriter postingsWriter = null;
    ArrayList dictDataFormat = new ArrayList(1);
    int rowLen = Integer.parseInt((String)indexFormat.get(2));
    ArrayList typeList = new ArrayList(rowLen);
    this.dataLength = new HashMap(5);
    this.numrecs = new HashMap(5);
    for (int i = 3 + rowLen, j = 0; i < 4 + rowLen + rowLen; i++, j++)
      {
	typeList.add(j, indexFormat.get(i));
	// System.out.println("type: " + indexFormat.get(i));
      }
    // System.out.println("indexFormat: " + StringUtils.list(indexFormat));
    // System.out.println("typeList: " + StringUtils.list(typeList));
    int dataLen = 0;
    ArrayList dataFormatList = new ArrayList(10);
    for (int i = 1; i < rowLen; i++ )
      {
	String fieldtype = (String)typeList.get(i);
	dataFormatList.add(binFormats.get(fieldtype));
      }
    // create index

    File indexDirectory = new File(this.indexParentDirectoryPath + "/" + this.indexname);
    if (indexDirectory.mkdir() == false)
      {
	throw new BSPIndexCreateException("unable to create index directory");
      }
    dictDataFormat.add(binFormats.get("PTR"));
    postingsWriter = new RunLengthPostingsWriter 
      (indexParentDirectoryPath + "/" + this.indexname );
    PrintWriter statfp = new PrintWriter
      (new BufferedWriter(new FileWriter( indexParentDirectoryPath + "/" +
					  this.indexname + "/partition.stats")));
    statfp.println( "# " + this.indexname + "/partition.log -- bsp_map.tcl status file" );
    statfp.println( "# total number of terms: " + this.wordnum );
    statfp.println( "#" );
    statfp.println( "# table format: " );
    statfp.println( "#  partition_filename termlen nterms" );

    PrintWriter rcfp = new PrintWriter
      (new BufferedWriter(new FileWriter( indexParentDirectoryPath + "/" +
					  this.indexname + "/mapinforc.tcl")));
    rcfp.println( "# Tcl rc file for bsp_map." );
    rcfp.println( "#" );
    rcfp.println( "# record format:" );
    rcfp.println( "#   " + StringUtils.join(indexFormat, "|") );
    rcfp.println( "bsp_map::mapformat " + this.indexname + " " +
		  StringUtils.list(indexFormat));
    rcfp.println( "bsp_map::index_org " + this.indexname + " TRUE INVERTED FILE ");
    rcfp.println( "bsp_map::dictdataformat " + this.indexname + " " +
		  StringUtils.join(dictDataFormat, " ") + " " + dataLen );
    
    rcfp.println( "# quick load partition map for Tcl" );
    rcfp.println( "# format: " );
    rcfp.println( "#  bsp_map::partition <mapname> <term length> <partitionfilename> <num of records>" );
 
    
    Iterator iter = this.hashlist.keySet().iterator();
    while (iter.hasNext()) 
      {
	String key = (String)iter.next();
	Map map = (Map)hashlist.get(key);
	
	buildInvertedFile(dictDataFormat, map, key, postingsWriter);
	int keylength = 0;
	Iterator mapIter = map.keySet().iterator();
	if (mapIter.hasNext()) {
	  keylength = ((String)mapIter.next()).length();
	}
	statfp.println( "partition_" + key + " " + 
			keylength + " " + map.size());
	rcfp.println( "bsp_map::partition " + this.indexname + " " +
		      keylength + " partition_" + key + " " + map.size());
      }
    
    postingsWriter.close();
    statfp.close();
    rcfp.close();
    // we've gotten this far, assume we have a valid index.
    this.valid = true;
    // System.out.println("Index info: \n" + this);

    /* serialize info on object to indexname/bspIndexInfo.ser */
    FileOutputStream ostream = 
      new FileOutputStream(this.indexParentDirectoryPath + "/" + this.indexname +
			   "/" + "bspIndexInfo.ser");
    ObjectOutputStream p = new ObjectOutputStream(ostream);
    
    p.writeObject(this);
    p.flush();
    p.close();
    ostream.close();
  }

  /**
   * Build index in inverted file organization.
   * @param dataFormat  format of data to be stored with key.
   * @param aTermMap    Map containing key/value pairs to be stored in index.
   * @param partitionId     partition identifier.
   * @param postingsWriter  postings file writer.
   */
  private void buildInvertedFile( ArrayList dataFormat, 
				  Map aTermMap, 
				  String partitionId, 
				  RunLengthPostingsWriter postingsWriter)
    throws IOException
  {
    int nextpost = 0;
    int numrecs = 0;
    DictionaryBinSearchMap intPartition = 
      new DictionaryBinSearchMap ( indexParentDirectoryPath + "/" +
				   this.indexname + "/partition_" + partitionId, 
				   BinSearchMap.WRITE );
    Iterator keyIter = aTermMap.keySet().iterator();
    while (keyIter.hasNext()) {
      String termKey = (String)keyIter.next();
      ArrayList postings = (ArrayList)aTermMap.get(termKey);
      Iterator postingIter = postings.iterator();
      
      if (postingIter.hasNext()) 
	{
	  String dataRecord = (String)postingIter.next();
	  // write posting
	  nextpost = postingsWriter.writeString(dataRecord);
	  while (postingIter.hasNext()) 
	    {
	      dataRecord = (String)postingIter.next();
	      // write posting
	      postingsWriter.writeString(dataRecord);
	    }
	}
      // write dictionary entry
      intPartition.writeEntry(termKey, postings.size(), nextpost);
    }
    this.numrecs.put(partitionId, new Integer(intPartition.getNumberOfRecords()));
    // System.out.println("key: " + key );
    this.dataLength.put(partitionId, new Integer(4));
    intPartition.close();
  }

  /**
   * If modification time of table file is later than index then rebuild index
   * using lisp file. See method "create".
   */
  public void update()
    throws IOException, BSPIndexCreateException
  {
    String indexDir = this.indexParentDirectoryPath + "/" + this.indexname;
    // System.out.println("updating index: " + this.indexname );
    File tablefile = new File(this.tablefilename);
    File mapfile = new File(indexDir);
    // System.out.println("mapfile.exists(): " + mapfile.exists());
    if (mapfile.exists() == false ||
	tablefile.lastModified() > mapfile.lastModified())  {
      this.load_map();
      this.create();
    } else if (mapfile.isFile())  {
	throw new IOException("file " + indexname + " is not a directory!");
    } 
  } // InvertedFile.update


  /**
   * setup newly read serialized index.
   */
  public void setup()
    throws BSPIndexInvalidException
  {
    if ( this.valid == false ) {
      throw new BSPIndexInvalidException("Index is not valid. run method update(), "+ this);
    }
    if ( this.partitionFiles == null ) {
      this.partitionFiles = new HashMap(4);
    }
  }

  /**
   * Look up word in index, return corresponding key and value pair if
   * found, null if otherwise.
   * @param word word to lookup in index.
   * @return tuple containing key/value pair, null if key not found.
   */
  public BSPTuple lookup(String word)
    throws FileNotFoundException, IOException
  {
    RandomAccessFile dictionaryFile;
    String keyLength = new Integer (word.length()).toString();
    String key = this.indexname + keyLength;
    // System.out.println("this.partitionFiles: " + this.partitionFiles );
    if ( this.partitionFiles.containsKey(key) ) 
      {
	dictionaryFile = (RandomAccessFile)this.partitionFiles.get(key);
      }
    else 
      {
	try {
	  dictionaryFile = 
	    new RandomAccessFile ( indexParentDirectoryPath + "/" +
				   indexname + "/partition_" + key, "r" );
	  this.partitionFiles.put(key, dictionaryFile);
	} catch (FileNotFoundException exception) {
	  return new BSPTuple(word, new ArrayList(0)); // partition not found, return an empty list
	}
      } 

    DictionaryEntry entry = 
      DiskBinarySearch.dictionaryBinarySearch(dictionaryFile, word, word.length(), 
					      ((Integer)this.numrecs.get(key)).intValue() );
    if (entry == null) {
      return new BSPTuple(word, new ArrayList(0)); // entry not found, return an empty list
    }
    int count = entry.getNumberOfPostings();
    int address = entry.getAddress();
    // System.out.println("postings count : " + count);
    // System.out.println("address : " + address);
    if ( this.postingsFile == null ) {
	  this.postingsFile = 
	    new RandomAccessFile ( indexParentDirectoryPath + "/" +
				   indexname + "/postings", "r" );
    }
    ArrayList postings = new ArrayList(count);
    postingsFile.seek(address);
    for (int i = 0; i < count; i++)
      {
	int postingsLen = postingsFile.readInt();
	// System.out.println("postingsLen : " + postingsLen);
	byte[] databuf = new byte[postingsLen];
	postingsFile.read(databuf);
	postings.add(new String(databuf));
      }
    return new BSPTuple(word, postings );
  }

  /**
   * Attempt to release resources used in index generation.
   */
  public void release() 
    throws IOException
  {
    Iterator partIter = this.partitionFiles.keySet().iterator();
    while (partIter.hasNext()) {
      String key = (String)partIter.next();
      ((RandomAccessFile)this.partitionFiles.get(key)).close();
      this.partitionFiles.remove(key);
    }
    if (this.postingsFile != null)  {
      this.postingsFile.close();
      this.postingsFile = null;
    }
  }

  /**
   * Implementation of toString to override default implementation in
   * java.lang.Object.
   * @return string representation of index object.
   */
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append(" indexname: ").append(indexname).append("\n");
    sb.append(" tablefilename: ").append(tablefilename).append("\n");
    sb.append(" indexParentDirectoryPath: ").append(indexParentDirectoryPath).append("\n");
    sb.append(" numrecs: ").append(numrecs).append("\n");
    sb.append(" dataLength: ").append(dataLength).append("\n");
    sb.append(" keyIndices: ").append(keyIndices).append("\n");
    sb.append(" partitionFiles: ").append(partitionFiles).append("\n");
    return sb.toString();
  }

  /**
   * main program 
   * @param argv argument vector.
   */
  public static void main(String[] argv)
  {
    
  }

}// InvertedFile
