org.eclipse.m2e.core 2.0.8 — Offline Maven Repository
=======================================================

Source:
  Eclipse 2023-06 release train p2 repository
  https://download.eclipse.org/releases/2023-06/

The jar was downloaded from the p2 plugins directory and installed using:

  mvn install:install-file \
    -Dfile=org.eclipse.m2e.core_2.0.8.20230520-0934.jar \
    -DgroupId=org.eclipse.m2e \
    -DartifactId=org.eclipse.m2e.core \
    -Dversion=2.0.8 \
    -Dpackaging=jar \
    -DlocalRepositoryPath=./local-repo \
    -DgeneratePom=true

Compatible with: Eclipse 2023-06, JavaSE-17
