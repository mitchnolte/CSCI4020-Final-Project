.PHONY: build run clean

name = final_project

CLASSPATH = /antlr-4.11.1-complete.jar:./target
JAVAC = javac -cp $(CLASSPATH)
ANTLR = java -jar /antlr-4.11.1-complete.jar -no-visitor -no-listener
TESTRIG = java -cp $(CLASSPATH) org.antlr.v4.gui.TestRig

build:
	mkdir -p ./target
	kotlinc -cp $(CLASSPATH) -d ./target ./src/backend/*.kt
	$(ANTLR) -o ./target/ ./src/*.g4
	$(JAVAC) -d ./target ./target/src/*.java

run:
	@jupyter nbconvert \
		--to notebook --execute worksheet.ipynb --output output.ipynb 2>&1 | \
		grep -v '\[main\]' | grep -v '\[IOPUB\]' | grep -v '\[Thread-' | \
	       	grep -v '\[NbConvertApp\]'

get:
	cp -R -T ~/public/$(name) ~/work/$(name)/

clean:
	rm -rf ./target
	rm -f ./output.ipynb
