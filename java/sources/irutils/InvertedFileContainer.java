package irutils;

import java.util.*;
import java.io.*;

/**
 * Inverted File Container,
 * keeps track of inverted file indices. Uses config file: <b>ifconfig</b>.
 * <p>
 * Format of file <b>ifconfig</b>:
 * <pre>
 * NUM_TABLES: 5
 * #
 * ############################################################################
 * #
 * # Format:
 * #
 * # input_file|tablename|num_fields|key fields|fieldname1|...|N|fieldtype1|...|N|
 * #
 * #          E.g.: stopwords.txt|stpwrds|2|0|word|score|TXT|TXT
 * # 
 * # Field Types: 
 * #     TXT  -- data you wish to be accessed as text (or floats)
 * #     INT  -- data you wish to be accessed as integers
 * # 
 * # Maximum Number of Columns: 10
 * # Maximum Filename Length: 30
 * # Maximum Filename Length: 30
 * # Maximum Fieldname Length: 20
 * #
 * # Key Fields: a comma separated list of columns to be used as key,
 * #             currently must be type TXT
 * #
 * # Processing:
 * #
 * #   To Add Entries:
 * #     1) Add line(s) @ bottom of file with your new table information
 * #     2) Increment first line (NUM_TABLES) to reflect additions
 * #     3) Run create_bulk from this directory
 * #
 * #   To Remove Entries:
 * #     1) Remove line(s) from file as necessary
 * #     2) Decrement first line (NUM_TABLES) to reflect deletions
 * #     3) There is no need to rerun create_bulk
 * #
 * ############################################################################
 * #
 * recommendations.txt|recommendations|3|0|pmid|marsid|recommendations|TXT|TXT|TXT
 * smallrecommend.txt|smallrecommend|3|0|pmid|marsid|recommendations
 * mrcon_filtered.txt|mrcon_filtered|8|0|cui|lat|ts|lui|stt|sui|str|lrl|TXT|TXT|TXT|TXT|TXT|TXT|TXT|TXT
 * mrcon_filtered.txt|mrcon_filtered_lat_ts_stt|8|1,2,3|cui|lat|ts|lui|stt|sui|str|lrl|TXT|TXT|TXT|TXT|TXT|TXT|TXT|TXT
 * MRSO|mrso_lat_ts_stt|7|4|cui|lui|sui|sab|tty|scd|srl|TXT|TXT|TXT|TXT|TXT|TXT|TXT
 * MRCON|mrcon|8|0|cui|lat|ts|lui|stt|sui|str|lrl|TXT|TXT|TXT|TXT|TXT|TXT|TXT|TXT
 * </pre>
 * Example of use:
 * <pre>
 *   String indexRoot = "/home/wrogers/devel/exper/irutils/java/indices";
 *   String tableRoot = "/home/wrogers/devel/exper/irutils/java/tables";
 *   String indexname = "mrcon";
 *   // open a container to keep track of the indices       
 *   InvertedFileContainer bspContainer =
 *           new InvertedFileContainer(tableRoot, indexRoot);
 *
 *   // get a index instance for "MRCON"
 *   InvertedFile index = container.get(indexname);
 *   if (index == null) 
 *     System.out.println("index is null");
 *
 *   // check to see if index exists, if not then create it. 
 *   index.update();
 *
 *   // setup index for retrieval.
 *   index.setup();
 *   
 *   BSPTuple result = index.lookup("C00001403");
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
 *</p>
 *<p>
 * Created: Mon Jul  9 21:57:05 2001
 *</p>
 * @author Will Rogers
 * @version $Id: InvertedFileContainer.java,v 1.7 2002/10/08 20:03:17 wrogers Exp $
 */

public class InvertedFileContainer {

  static final String CONFIG_FILENAME = "ifconfig";

  /** directory in which indices resides */
  String indexRoot;

  /** directory in which tables resides */
  String tableRoot;

  /** number of tables in database, initialized or not. */
  int numTables;

  /** map of tables and their configurations */
  HashMap tableMap = new HashMap(10);
  
  /** map of open indices */
  HashMap openIndexMap = new HashMap(10);

  /** default constructor for serialization purposes. */
  public InvertedFileContainer()
  {
  }

  /**
   * @param tableRoot directory where indices reside.
   * @param indexRoot directory where table reside.
   */
  public InvertedFileContainer(String tableRoot, String indexRoot)
    throws NumberFormatException, FileNotFoundException, IOException
  { 
    this.tableRoot = tableRoot; 
    this.indexRoot = indexRoot;
    this.loadConfig();
  }

  /** Load list of tables and their configurations. */
  public void loadConfig() 
    throws NumberFormatException, FileNotFoundException, IOException
  {
    String line = null;

    BufferedReader reader = 
      new BufferedReader(new FileReader( this.tableRoot + File.separator + CONFIG_FILENAME ));
    if ( (line = reader.readLine()) != null ) {
      this.numTables = Integer.parseInt((String)utils.StringUtils.getToken(line, " ", 1));
    } else {
      // should throw an exception here...
      System.err.println("invalid config file, first line: \"NUM_TABLES: <n>\" missing");
    }

    while ( (line = reader.readLine()) != null )
      {
	if ( line.length() > 0 && line.charAt(0) != '#' ) {
	  String tablename = utils.StringUtils.getToken(line, "|", 1);
	  // System.out.println("table: " + tablename);
	  this.tableMap.put(tablename, line);
	}
      }
    reader.close();
  }

 /**
  * Get map of tables and their corresponding configurations.
  * @return map containing tables and configurations.
  */
  public Map getTableConfigMap()
  {
    return this.tableMap;
  }

  /**
   * set directory where indices reside.
   * @param root directory where indices reside.
   */
  public void setIndexRoot(String root) 
  {
    this.indexRoot = root;
  }

  /**
   * set directory where tables reside.
   * @param root directory where tables reside.
   */
  public void setTableRoot(String root)
  {
    this.tableRoot = root;
  }

  /** 
   * get index object for index specified by indexname.
   * @param indexname name of index to be instantiated.
   * @return index instance.
   */
  public InvertedFile get(String indexname)
    throws FileNotFoundException, StreamCorruptedException, IOException,
           ClassNotFoundException, OptionalDataException
  {
    /** index open? if so then use it. */
    InvertedFile invertedFile = (InvertedFile)openIndexMap.get(indexname);
    if (invertedFile != null)
      return invertedFile;

    /** otherwise instantiate index */
    String line = (String)this.tableMap.get(indexname);
    if (line != null) {
      List formatList = utils.StringUtils.split(line, "|");
      
      String serializedInfo =  indexRoot + File.separator + indexname + File.separator + 
	InvertedFile.canonicalSerializedName;
      if ( new File(serializedInfo).exists() ) {
	// System.out.println(" loading " + serializedInfo);
	FileInputStream istream = new FileInputStream(serializedInfo);
	ObjectInputStream p = new ObjectInputStream(istream);
	InvertedFile index = (InvertedFile)p.readObject();
        index.tablefilename = indexRoot;
        index.indexParentDirectoryPath = tableRoot;
	istream.close();
	this.openIndexMap.put(indexname, index);
	return index;
      } else {
	InvertedFile index = 
	  new InvertedFile((String)formatList.get(1), 
			   this.tableRoot + File.separator + (String)formatList.get(0),
			   this.indexRoot,
			   formatList);
	this.openIndexMap.put(indexname, index);
	return index;
      }
    } 
    return null;
  }

  /**
   * Print configuration of index object instance.
   * @param out print stream to write configuration to.
   */
  public void printConfig(java.io.PrintStream out)
  {
    out.println("Table Root: " + this.tableRoot);
    out.println("Index Root: " + this.indexRoot);
    out.println("Tables list:");
    Iterator iter = this.tableMap.values().iterator();
    while (iter.hasNext()) {
      out.println(iter.next());
    }
  }

  /**
   * Print configuration of index object instance.
   */
  public void printConfig()
  {
    printConfig(System.out);
  }

  /**
   * Implementation of statndard method toString().
   * @return string representation of container object.
   */
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("Table Root: ").append(this.tableRoot).append("\n");
    sb.append("Index Root: ").append(this.indexRoot).append("\n");
    sb.append("Tables list:").append("\n");
    Iterator iter = this.tableMap.values().iterator();
    while (iter.hasNext()) {
      sb.append(iter.next()).append("\n");
    }
    return sb.toString();
  }

  /**
   * implementation of Object's finalize method.
   */
  protected void finalize()
    throws Throwable
  {
    for (Iterator iterator = openIndexMap.keySet().iterator(); 
	 iterator.hasNext(); 
	 ((InvertedFile)iterator.next()).finalize());
    super.finalize();
  }

  /**
   * main program 
   * @param argv argument vector.
   */
  public static void main(String[] argv) 
    throws FileNotFoundException, IOException
  {
    InvertedFileContainer bspMap = 
      new InvertedFileContainer("tables", "indices");
    bspMap.printConfig();
  }

} // InvertedFileContainer
