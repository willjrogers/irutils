package irutils;

import java.util.*;
import java.io.*;

/**
 * Container for BSP Indices.
 *
 *
 * Created: Mon Jul  9 21:57:05 2001
 *
 * @author Will Rogers
 * @version $Id: BSPContainer.java,v 1.7 2001/09/20 15:01:50 wrogers Exp $
 */

public class BSPContainer {

  static final String CONFIG_FILENAME = "config";

  /** directory in which indices resides */
  String indexRoot;

  /** directory in which tables resides */
  String tableRoot;

  /** number of tables in database, initialized or not. */
  int numTables;

  /** map of tables and their configurations */
  HashMap tableMap = new HashMap(20);

  /** default constructor for serialization purposes. */
  public BSPContainer()
  {
  }

  /**
   * @param tableRoot directory where indices reside.
   * @param indexRoot directory where table reside.
   */
  public BSPContainer(String tableRoot, String indexRoot)
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
      new BufferedReader(new FileReader( this.tableRoot + "/" + CONFIG_FILENAME ));
    if ( (line = reader.readLine()) != null ) {
      this.numTables = Integer.parseInt((String)irutils.StringUtils.getToken(line, " ", 1));
    } else {
      // should throw an exception here...
      System.err.println("invalid config file, first line: \"NUM_TABLES: <n>\" missing");
    }

    while ( (line = reader.readLine()) != null )
      {
	if ( line.length() > 0 && line.charAt(0) != '#' ) {
	  String tablename = irutils.StringUtils.getToken(line, "|", 1);
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
  public BSPIndex get(String indexname)
    throws FileNotFoundException, StreamCorruptedException, IOException,
           ClassNotFoundException, OptionalDataException
  {

    String line = (String)this.tableMap.get(indexname);
    if (line != null) {
      List formatList = irutils.StringUtils.split(line, "|");
      
      String serializedInfo =  indexRoot + "/" + indexname + "/" + 
	"bspIndexInfo.ser";
      if ( new File(serializedInfo).exists() ) {
	System.out.println(" loading " + serializedInfo);
	FileInputStream istream = new FileInputStream(serializedInfo);
	ObjectInputStream p = new ObjectInputStream(istream);
	BSPIndex index = (BSPIndex)p.readObject();
	istream.close();
	// System.out.println("index:\n" + index);
	return index;
      } else {
	return new BSPIndex((String)formatList.get(1), 
			    this.tableRoot + "/" + (String)formatList.get(0),
			    this.indexRoot,
			    formatList);
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
   * main program 
   * @param argv argument vector.
   */
  public static void main(String[] argv) 
    throws FileNotFoundException, IOException
  {
    BSPContainer bspMap = 
      new BSPContainer("/home/wrogers/devel/exper/utils/java/tables",
		       "/home/wrogers/devel/exper/utils/java/indices");
    bspMap.printConfig();
  }

} // BSPContainer
