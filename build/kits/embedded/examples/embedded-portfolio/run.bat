SET TEIID_PATH=..\..\lib\*;..\..\optional\file\translator-file-${pom.version}.jar;..\..\optional\file\connector-file-${pom.version}-lib.jar;..\..\optional\jbdc\translator-jdbc-${pom.version}.jar;h2-1.3.161.jar

javac -cp %TEIID_PATH% src\org\teiid\example\*.java 

java -cp .\src;%TEIID_PATH% org.teiid.example.TeiidEmbeddedPortfolio %*

