package irutils;

import java.util.List;
import java.util.Iterator;

/** IFBuild, a sample based command line index builder for the InvertedFile class.
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: IFBuild.java,v 1.3 2002/10/08 19:06:45 wrogers Exp $
 */

public class IFBuild {

  /** set verbosity via property <code>-Difbuild.verbose=</code><em>true|false</em> */
  static boolean verbose = 
    Boolean.getBoolean(System.getProperty("ifbuild.verbose", "false"));

  /**
   * main program 
   * @param args argument vector.
   *
   * usage: irutils.IFBuild <indexname>
   * properties:
   *   index.path=<directory path> : where path of indices resides
   *   table.path=<directory path> : where tables reside
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
      System.out.println("usage: irutils.IFBuild <indexname>");
      System.out.println("properties: ");
      System.out.println("  -Dindex.path=<directory path> : where path indices resides");
      System.out.println("   (default: " + indexPath + ")");
      System.out.println("  -Dtable.path=<directory path> : where tables reside");
      System.out.println("   (default: " + tablePath + ")");
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
	System.exit(1);
      }
    if (IFBuild.verbose)
      System.out.println("using index: " + index);

    // check to see if index exists, if not then create it. 
    index.update();

    // setup index for retrieval.
    index.setup();

    // release index 
    index.release();
  }
}// IFBuild
