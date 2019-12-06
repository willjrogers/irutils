package irutils;

import java.io.IOException;

/**
 * DiskBasedBinSearchMap extended interface for Disk based
 * BinSearchMaps
 *
 *
 * Created: Fri Dec  6 08:58:16 2019
 *
 * @author <a href="mailto:wjrogers@mail.nih.gov">Willie Rogers</a>
 * @version 1.0
 */
public interface DiskBasedBinSearchMap extends BinSearchMap {
  /**
   * Write an entry into map
   * @param term Term. 
   * @param numOfPostings Number of postings assoicated with term.
   * @param data data associated with term.
   */
  void writeEntry(String term, int numOfPostings, int data) throws IOException;
}
