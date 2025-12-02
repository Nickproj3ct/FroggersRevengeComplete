class Lane {
    final int y;        // top pixel of the lane
    final double speed; // +right, -left
    final int spawnEveryTicks;
    public int spawnEvery;
    Lane(int y, double speed, int spawnEveryTicks) {
        this.y = y;
        this.speed = speed;
        this.spawnEveryTicks = spawnEveryTicks;
    }
}

