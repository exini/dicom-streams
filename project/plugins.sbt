resolvers += Resolver.typesafeRepo("releases")

resolvers += Classpaths.typesafeReleases

addSbtPlugin("com.softwaremill.sbt-softwaremill" % "sbt-softwaremill-common" % "2.0.18")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"          % "1.5.12")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"           % "0.11.0")
addSbtPlugin("de.heikoseeberger"                 % "sbt-header"              % "5.10.0")
addSbtPlugin("org.scoverage"                     % "sbt-scoverage"           % "2.0.9")
addSbtPlugin("org.scoverage"                     % "sbt-coveralls"           % "1.3.11")
