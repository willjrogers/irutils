package utils;
import java.io.*;

/**
 * BinSearchMap.java
 *
 *
 * Created: Wed Jul 25 09:11:23 2001
 *
 * @author <a href="mailto: "Willie Rogers</a>
 * @version $Id: BinSearchMap.java,v 1.3 2001/08/17 17:19:14 wrogers Exp $
 */

public interface BinSearchMap 
{
  /** open map for reading */
  public static final int READ = 0;
  /** open map for writing */
  public static final int WRITE = 1;

  /**
   * @return get number of records in map
   */
  int getNumberOfRecords();

  /**
   * @return get length of data in each record
   */
  int getDataLength();
 
  /** close resources used by this map. */
  void close() throws IOException;

} // BinSearchMap
