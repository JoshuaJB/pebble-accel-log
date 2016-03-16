package me.jbakita.pebbledatalogging;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Storage for one accelerometer reading. This reading is comprised of x, y, z,
 * and time fields.
 */
public class AccelerometerReading {
    // Directional vectors
    private int x;
    private int y;
    private int z;
    // POSIX time in ms that this reading was taken at
    private long timestamp = 0;

    /**
     * Initialize a reading. We require that a timestamp be added later.
     * @param x The X vector of acceleration
     * @param y The Y vector of acceleration
     * @param z The Z vector of acceleration
     */
    public AccelerometerReading(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Set reading timestamp
     * @param posixMSTime time in ms since UNIX epoch
     */
    public void setTimestamp(long posixMSTime) {
        timestamp = posixMSTime;
    }

    /**
     * Get the X-directed acceleration
     * @return The integer representation of X
     */
    public int getX() {
        return x;
    }

    /**
     * Get the X-directed acceleration
     * @return The integer representation of X
     */
    public int getY() {
        return y;
    }

    /**
     * Get the X-directed acceleration
     * @return The integer representation of X
     */
    public int getZ() {
        return z;
    }

    /**
     * Retrieve the vector magnitude of this reading.
     * (computed using extended pythogorian theorem)
     */
    public double getMagnitude() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Retrieve a descriptive header of the CSV fields output by toCSV().
     */
    public String CSVHeader() {
        return "Time(ms),X(mG),Y(mG),Z(mG)";
    }

    /**
     * Retrive a machine-readable string of this reading in CSV format
     */
    public String toCSV() {
        return String.format(Locale.US, "%14d,%+5d,%+5d,%+5d\n", timestamp, x, y, z);
    }

    /**
     * Retrive a human-readable string of this reading
     */
    @Override
    public String toString() {
        return String.format("\nX: %+5d, Y: %+5d, Z: %+5d, Time: %s", x, y, z, DateFormat.getDateTimeInstance().format(new Date(timestamp)));
    }
}
