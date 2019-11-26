package com.conveyal.r5.analyst;

import com.vividsolutions.jts.geom.Envelope;
import gnu.trove.list.TIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A PointSet represents a set of geographic points, which serve as destinations or "opportunities" in an
 * accessibility analysis. Legacy Transport Analyst used freeform pointsets; early versions of Conveyal Analysis
 * instead favored regular grids in the web mercator projection.  This abstraction encompasses both.
 * TODO PointSet should probably become an interface to hide all this spatial indexing and such.
 */
public abstract class PointSet {

    private static final Logger LOG = LoggerFactory.getLogger(PointSet.class);

    /**
     * It seems like fighting Java typing to store type codes in JSON.
     * But at least by using some symbolic constants and Java identifiers things are well cross-referenced.
     * This is mostly used to store metadata for both grids and freeform points in the same Mongo collection.
     */
    public enum Format {
        FREEFORM (FreeFormPointSet.FILE_EXTENSION),
        GRID (Grid.FILE_EXTENSION);
        public final String fileExtension;
        Format(String fileExtension) {
            this.fileExtension = fileExtension;
        }
    }

    /**
     * Human readable name. Unfortunately this is lost when persisting Grids, to maintain backward compatibility.
     * TODO make this a method
     */
    public transient String name;

    /**
     * Returns a list of indexes for all points in the PointSet that are at least partially inside the envelope.
     * This may overselect or contain duplicate point indexes (though implementations should minimize those effects).
     * TODO: Add tests for implementation(s).
     *
     * @param envelope the envelope in FIXED POINT DEGREES within which we want to find all points.
     * @return a list of indexes for all points in the PointSet at least partially inside the envelope.
     */
    public TIntList getPointsInEnvelope(Envelope envelope) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the WGS84 latitude of point i in the PointSet. In the general case, all PointSets (even those on grids)
     *         are treated as flattened one-dimensional arrays.
     */
    public abstract double getLat(int i);

    /**
     * @return the WGS84 longitude of point i in the PointSet. In the general case, all PointSets (even those on grids)
     *         are treated as flattened one-dimensional arrays.
     */
    public abstract double getLon(int i);

    /**
     * @return the total number of points in the PointSet. In the general case, all PointSets (even those on grids) are
     *         treated as flattened one-dimensional arrays, so a gridded PointSet has (width * height) points.
     */
    public abstract int featureCount();

    /**
     * @return the sum of the opportunity counts at all points in this PointSet.
     */
    public abstract double sumTotalOpportunities();

    /**
     * @param i the one-dimensional index into the list of points.
     * @return the quantity or magnitude of opportunities at that point (e.g. jobs, people)
     */
    public abstract double getOpportunityCount(int i);

    /**
     * @param i the one-dimensional index into the list of points.
     * @return a unique ID string for this particular point within the scope of this pointset.
     */
    public String getId (int i) {
        return Integer.toString(i);
    }

}
