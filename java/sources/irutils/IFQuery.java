package irutils;

import java.util.List;
import java.util.Iterator;

/** IFQuery, a sample command line interface for the InvertedFile class.
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: IFQuery.java,v 1.6 2002/03/19 15:08:37 wrogers Exp $
 */

public class IFQuery {

  /** set verbosity via property <code>-Difquery.verbose=</code><em>true|false</em> */
  static boolean verbose = 
    Boolean.getBoolean(System.getProperty("ifquery.verbose", "false"));

  /**
   * main program 
   * @param args argument vector.
   *
   * usage: irutils.IFQuery <indexname> <queryterm> [| <queryterm>]
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

    if (args.length < 2) {
      System.out.println("usage: irutils.IFQuery <indexname> <queryterm> [| <queryterm>]");
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
	System.exit(1);
      }
    if (IFQuery.verbose)
      System.out.println("using index: " + index);

    // check to see if index exists, if not then create it. 
    index.update();

    // setup index for retrieval.
    index.setup();

    words = new String[args.length - 1];
    for (int i = 1; i < args.length; i++) {
      words[i-1] = args[i];
    }

    for (int i = 0; i < words.length; i++) {
      // lookup each term in index.
      BSPTuple result = index.lookup(words[i]);
      List list = (List)result.getValue();
      for (Iterator j = list.iterator(); j.hasNext(); ) {
	System.out.println(j.next());
      }
    }
    index.release();
  }
}// IFQuery
