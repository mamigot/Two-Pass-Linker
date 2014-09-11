import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// scp -r TwoPassLinker/ ma2786@i5.nyu.edu:
public class TwoPass {

	enum Data {
		DEFINITIONS, USES, INSTRUCTIONS,

		// Subcategories that characterize a definition
		SYMBOL, LOCATION,
		// Subcategories that characterize an instruction
		// (for example, in "R 1004", "R" is the type and "1004" is the word,
		// which is made of an opcode and an address)
		TYPE, WORD;

		static Data current = getFirst();

		public static Data getFirst() {
			return current = Data.DEFINITIONS;
		}

		public static Data getNext() {
			if (current == Data.DEFINITIONS)
				return current = Data.USES;

			else if (current == Data.USES)
				return current = Data.INSTRUCTIONS;

			else
				return current = Data.DEFINITIONS;
		}
	}

	class Symbol {
		public String symbol;
		public Integer location;
		
		public Symbol(String symbol, Integer location){
			this.symbol = symbol;
			this.location = location;
		}
		
		public Symbol(String symbol){
			this(symbol, null);
		}
		
		public String toString(){
			return this.symbol + "=" + this.location;
		}
	}

	class TextInstruction {
		public Character classification;
		public Integer opcode;
		public Integer address;

		public TextInstruction(Character classification, Integer opcode, Integer address){
			this.classification = classification;
			this.opcode = opcode;
			this.address = address;
		}
		
		public TextInstruction(Character classification){
			this(classification, null, null);
		}
		
		public String toString() {
			return (this.opcode * 1000) + this.address + "";
		}
	}

	class Module {
		public int startLocation;
		public int endLocation;
		public int length;
		
		public List<Symbol> symbols;
		public List<TextInstruction> textInstructions;
		
		public Module(int startLocation){
			this.startLocation = startLocation;
			
			this.endLocation = startLocation;
			this.length = 0;
			
			this.symbols = new ArrayList<Symbol>();
			this.textInstructions = new ArrayList<TextInstruction>();
		}
		
		public void addSymbol(Symbol symbol){
			this.symbols.add(symbol);
		}
		
		public void addInstruction(TextInstruction instruction){
			this.textInstructions.add(instruction);
			
			this.length++;
			this.endLocation = this.startLocation + this.length;
		}
	}

	private ArrayList<Module> modules = new ArrayList<Module>();
	private ArrayList<Symbol> symbols = new ArrayList<Symbol>();
	
	/**
	 * Last-visited and incomplete items as the data is being processed.
	 */
	private Symbol tempSymbol;
	private TextInstruction tempInstruction;
	private Module currModule;

	public TwoPass(String inputFilePath) throws FileNotFoundException {
		// Save all of the content into a variable
		Scanner scanner = new Scanner(new File(inputFilePath));
		String content = scanner.useDelimiter("\\A").next();
		// Identifies the tokens using RegEx
		Matcher matcher = Pattern.compile("[\\d\\w]+").matcher(content);

		// Type that will be visited first
		Data nextType = Data.getFirst();

		int remainingDefinitions = 0;
		int remainingUses = 0;
		int remainingInstructions = 0;

		String token;
		while (matcher.find()) {
			token = matcher.group();

			if (remainingDefinitions > 0) {
				if (remainingDefinitions % 2 == 0)
					this.processDefinition(token, Data.SYMBOL);
				else
					this.processDefinition(token, Data.LOCATION);

				remainingDefinitions--;
				if (remainingDefinitions == 0)
					nextType = Data.getNext();
				continue;
			}

			else if (remainingUses > 0) {
				this.processUse(token);

				remainingUses--;
				if (remainingUses == 0)
					nextType = Data.getNext();
				continue;
			}

			else if (remainingInstructions > 0) {
				if (remainingInstructions % 2 == 0)
					this.processInstruction(token, Data.TYPE);
				else
					this.processInstruction(token, Data.WORD);

				remainingInstructions--;
				if (remainingInstructions == 0)
					nextType = Data.getNext();
				continue;
			}

			// Only case left: a "remaining" counter
			// (the numbers before the definitions, uses, instructions...)
			else {
				// Check whatever nextType is and update that value
				int numNewElements = Integer.parseInt(token);
				// Nothing to see here... move along
				if (numNewElements == 0)
					nextType = Data.getNext();

				// Definitions come in groups of two (symbol + location)
				if (nextType == Data.DEFINITIONS){
					// The new module starts! The next will not start until
					// the next set of definitions is reached.
					this.initializeModule();
					remainingDefinitions = numNewElements * 2;
				}

				else if (nextType == Data.USES)
					remainingUses = numNewElements;

				// Instructions come in groups of two (type + word)
				else if (nextType == Data.INSTRUCTIONS)
					remainingInstructions = numNewElements * 2;
			}
		}
	}

	private void initializeModule(){
		// Get its start location on memory
		// Equivalent to the final word of the previous module + 1
		// (unless there are no modules; in that case, it's 0)
		int startLocation = 0;
		
		if(!this.modules.isEmpty()){
			int lastModuleIndex = this.modules.size() - 1;
			startLocation = this.modules.get(lastModuleIndex).endLocation + 1;
		}
		
		this.currModule = new Module(startLocation);
		// Add this to the global list of modules
		// (it will be updated elsewhere through this.currModule)
		this.modules.add(currModule);
	}
	
	private void processDefinition(String element, Data partType) {

		if (partType == Data.SYMBOL) {
			this.tempSymbol = new Symbol(element);

		} else if (partType == Data.LOCATION) {
			String symbol = this.tempSymbol.symbol;
			int location = Integer.parseInt(element);
			
			// Full symbol
			this.tempSymbol = new Symbol(symbol, location);
			
			/*
			 * Process the definition
			 */

			// Don't need this anymore
			this.tempSymbol = null;
		}

	}

	private void processUse(String element) {

		//System.out.println("use symbol:" + element);

	}

	private void processInstruction(String element, Data partType) {

		if (partType == Data.TYPE) {
			char classification = element.charAt(0);
			this.tempInstruction = new TextInstruction(classification);

		} else if (partType == Data.WORD) {
			char classification = this.tempInstruction.classification;
			int opcode = Character.getNumericValue(element.charAt(0));
			int address = Integer.parseInt(element.substring(1));
			
			// Full instruction
			this.tempInstruction = new TextInstruction(classification, opcode, address);
			
			/* Start the module */
			
			

			// Don't need this anymore
			this.tempInstruction = null;
		}

	}

	public static void main(String[] args) throws IOException {

		String filePath = "inputs/input-2.txt";

		TwoPass tp = new TwoPass(filePath);

	}

}
