package utils;

import java.util.*;
import java.io.*;

/**
 * BSPContainer.java
 *
 *
 * Created: Mon Jul  9 21:57:05 2001
 *
 * @author Will Rogers
 * @version $Id: BSPContainer.java,v 1.2 2001/07/25 18:45:39 wrogers Exp $
 */

public class BSPContainer {

  static final String CONFIG_FILENAME = "config";

  /** directory in which indices resides */
  String indexRoot;

  /** directory in which tables resides */
  String tableRoot;

  /** number of tables in database, initialized or not. */
  int numTables;

  HashMap tableMap = new HashMap(20);

  /** default constructor for serialization purposes. */
  public BSPContainer()
  {
  }
  
  public BSPContainer(String tableRoot, String indexRoot)
    throws NumberFormatException, FileNotFoundException, IOException
  { 
    this.tableRoot = tableRoot; 
    this.indexRoot = indexRoot;
    this.loadConfig();
  }

  public void loadConfig() 
    throws NumberFormatException, FileNotFoundException, IOException
  {
    String line = null;

    BufferedReader reader = 
      new BufferedReader(new FileReader( this.tableRoot + "/" + CONFIG_FILENAME ));
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

  public void setIndexRoot(String root) 
  {
    this.indexRoot = root;
  }

  public void setTableRoot(String root)
  {
    this.tableRoot = root;
  }

  public BSPIndex get(String indexname)
    throws FileNotFoundException, StreamCorruptedException, IOException,
           ClassNotFoundException, OptionalDataException
  {

    String line = (String)this.tableMap.get(indexname);
    if (line != null) {
      ArrayList formatList = utils.StringUtils.split(line, "|");
      
      String serializedInfo =  indexRoot + "/" + indexname + "/" + 
	"bspIndexInfo.ser";
      if ( new File(serializedInfo).exists() ) {
	System.out.println(" loading " + serializedInfo);
	FileInputStream istream = new FileInputStream(serializedInfo);
	ObjectInputStream p = new ObjectInputStream(istream);
	BSPIndex index = (BSPIndex)p.readObject();
	istream.close();
	// System.out.println("index: " + index);
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

  public void printConfig()
  {
    printConfig(System.out);
  }

  public static void main(String[] argv) 
    throws FileNotFoundException, IOException
  {
    BSPContainer bspMap = 
      new BSPContainer("/home/wrogers/devel/exper/utils/java/tables",
		       "/home/wrogers/devel/exper/utils/java/indices");
    bspMap.printConfig();
  }

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

} // BSPContainer
