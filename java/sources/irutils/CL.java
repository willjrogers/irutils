package utils;

/** command line interface for bspIndex */
public class CL {

  public static void main(String[] args)
    throws java.io.FileNotFoundException,
    java.io.IOException, BSPIndexCreateException, ClassNotFoundException
  {
    String words[];
    String indexRoot = "/home/wrogers/devel/exper/utils/java/indices";
    String tableRoot = "/home/wrogers/devel/exper/utils/java/tables";
    String indexname = "recommendations";
      
    BSPContainer bspContainer = new BSPContainer(tableRoot, indexRoot);
    bspContainer.printConfig();
    BSPIndex bspIndex = bspContainer.get(indexname);
    bspIndex.update();
    bspIndex.setup();

    if ( args.length == 0 ) {
      words = new String[] { "11409262" };
    } else {
      words = args;
    }
    for (int i = 0; i < words.length; i++) {
      BSPTuple result = bspIndex.lookup(words[i]);
      System.out.println("result: " + result);
    }
    bspIndex.release();
  }
}
