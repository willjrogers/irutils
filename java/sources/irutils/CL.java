package utils;

/** sample command line interface for bspIndex */
public class CL {

  /**
   * main program 
   * @param args argument vector.
   */
  public static void main(String[] args)
    throws java.io.FileNotFoundException,
    java.io.IOException, BSPIndexCreateException, BSPIndexInvalidException, 
    ClassNotFoundException
  {
    String words[];
    String indexRoot = "/home/wrogers/devel/exper/irutils/java/indices";
    String tableRoot = "/home/wrogers/devel/exper/irutils/java/tables";
    String indexname = "recommendations";

    // open a container to keep track of the indices       
    BSPContainer bspContainer = new BSPContainer(tableRoot, indexRoot);

    // get a index instance for "recommendations"
    BSPIndex bspIndex = bspContainer.get(indexname);

    // check to see if index exists, if not then create it. 
    bspIndex.update();

    // setup index for retrieval.
    bspIndex.setup();

    if ( args.length == 0 ) {
      words = new String[] { "11409262" };
    } else {
      words = args;
    }
    for (int i = 0; i < words.length; i++) {
      // lookup each term in index.
      BSPTuple result = bspIndex.lookup(words[i]);
      System.out.println("result: " + result);
    }
    bspIndex.release();
  }
}
