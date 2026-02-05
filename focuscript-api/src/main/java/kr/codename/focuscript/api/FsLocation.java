package kr.codename.focuscript.api;

public interface FsLocation {
    String getWorldName();
    double getX();
    double getY();
    double getZ();
    float getYaw();
    float getPitch();

    static FsLocation of(String worldName, double x, double y, double z, float yaw, float pitch) {
        return new FsSimpleLocation(worldName, x, y, z, yaw, pitch);
    }

    static FsLocation of(String worldName, double x, double y, double z) {
        return new FsSimpleLocation(worldName, x, y, z, 0.0f, 0.0f);
    }

    record FsSimpleLocation(
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) implements FsLocation {
        @Override
        public String getWorldName() {
            return worldName;
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getY() {
            return y;
        }

        @Override
        public double getZ() {
            return z;
        }

        @Override
        public float getYaw() {
            return yaw;
        }

        @Override
        public float getPitch() {
            return pitch;
        }
    }
}
