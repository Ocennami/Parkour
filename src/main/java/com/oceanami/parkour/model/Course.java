package com.oceanami.parkour.model;

import java.util.Objects;

public class Course {
    private final int id;
    private final String name;
    private boolean ready;

    public Course(int id, String name, boolean ready) {
        this.id = id;
        this.name = name;
        this.ready = ready;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return id == course.id && name.equals(course.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
