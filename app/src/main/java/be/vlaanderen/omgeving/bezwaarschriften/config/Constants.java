package be.vlaanderen.omgeving.bezwaarschriften.config;

public final class Constants {

  public static final String SPRING_PROFILE_BUILD = "build";
  public static final String SPRING_PROFILE_DEVELOPMENT = "dev";
  public static final String SPRING_PROFILE_PRODUCTION =
      "(!" + SPRING_PROFILE_BUILD + "&& !" + SPRING_PROFILE_DEVELOPMENT + ")";

  private Constants() {}
}
