package utils;

import java.util.ArrayList;
import java.util.Iterator;

/** IFQuery, a sample command line interface for the InvertedFile class.
 *
 * @author <a href="mailto:wrogers@nlm.nih.gov">Willie Rogers</a>
 * @version $Id: IFQuery.java,v 1.1 2001/08/31 19:21:53 wrogers Exp $
 */

public class IFQuery {

  /**
   * main program 
   * @param args argument vector.
   */
  public static void main(String[] args)
    throws java.io.FileNotFoundException,
    java.io.IOException, BSPIndexCreateException, BSPIndexInvalidException, 
    ClassNotFoundException
  {

    String words[] = { };	// empty array
    String indexRoot = "/home/wrogers/devel/exper/irutils/java/indices";
    String tableRoot = "/home/wrogers/devel/exper/irutils/java/tables";

    if (args.length < 2) {
      System.out.println("usage: utils.CL2 <indexname> <queryterm> [| <queryterm>]");
      System.exit(0);
    }

    String indexname = args[0];

    // open a container to keep track of the indices       
    InvertedFileContainer container = 
      new InvertedFileContainer(tableRoot, indexRoot);

    // get a index instance for "recommendations"
    InvertedFile index = container.get(indexname);
    if (index == null)
      {
	System.err.println("error creating index for " + indexname);
	System.exit(1);
      }
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
      ArrayList list = (ArrayList)result.getValue();
      for (Iterator j = list.iterator(); j.hasNext(); ) {
	System.out.println(j.next());
      }
    }
    index.release();
  }
}// IFQuery
