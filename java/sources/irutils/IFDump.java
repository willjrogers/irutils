package irutils;

/**
 * Describe class IFDump here.
 *
 *
 * Created: Thu Aug  2 12:51:27 2007
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version 1.0
 */
public class IFDump {

  /**
   * Creates a new <code>IFDump</code> instance.
   *
   */
  public IFDump() {

  }


  /**
   * main program 
   * @param args argument vector.
   *
   * usage: irutils.IFDump <indexname>
   * properties:
   *   index.path=<directory path> : where path of indices resides
   *   table.path=<directory path> : where tables reside
   */
  public static void main(String[] args)
    throws java.io.FileNotFoundException,
    java.io.IOException, BSPIndexCreateException, BSPIndexInvalidException, 
    ClassNotFoundException
  {
    String indexPath = 
      System.getProperty("index.path", 
			 "/home/wrogers/devel/exper/irutils/java/indices");
    String tablePath =
      System.getProperty("table.path",
			 "/home/wrogers/devel/exper/irutils/java/tables");

    if (args.length < 1) {
      System.out.println("usage: irutils.IFDump <indexname>");
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

  }

}


