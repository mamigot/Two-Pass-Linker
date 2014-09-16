import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
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
		/**
		 * Starts from 1
		 */
		public Integer moduleNumber;

		public boolean inModuleUseList;
		public boolean inInstructionList;

		public Symbol(String symbol, Integer location) {
			this.symbol = symbol;
			this.location = location;

			this.inModuleUseList = false;
			this.inInstructionList = false;
		}

		public Symbol(String symbol) {
			this(symbol, null);
		}

		public void isInUseList() {
			this.inModuleUseList = true;
		}

		public void isInInstructionList() {
			this.inInstructionList = true;
		}

		/**
		 * Judges equality based solely on the symbol (the String).
		 */
		public boolean equals(Symbol that) {
			if (this.symbol.equals(that.symbol))
				return true;
			else
				return false;
		}

		public String toString() {
			if (this.location != null) {
				StringBuilder sb = new StringBuilder();
				sb.append(this.symbol + "=" + this.location + "\n");
				sb.append("inModuleUseList = " + this.inModuleUseList + "\n");
				sb.append("inInstructionList = " + this.inInstructionList
						+ "\n");
				return sb.toString();

			} else
				return this.symbol;
		}
	}

	class TextInstruction {
		public Character classification;
		public Integer opcode;
		public Integer address;

		public TextInstruction(Character classification, Integer opcode,
				Integer address) {
			this.classification = classification;
			this.opcode = opcode;
			this.address = address;
		}

		public TextInstruction(Character classification) {
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

		public List<Symbol> definitions;
		public List<Symbol> uses;
		public List<TextInstruction> textInstructions;

		public Module(int startLocation) {
			this.startLocation = startLocation;

			this.endLocation = startLocation;
			this.length = 0;

			this.definitions = new ArrayList<Symbol>();
			this.uses = new ArrayList<Symbol>();
			this.textInstructions = new ArrayList<TextInstruction>();
		}

		public void addDefinition(Symbol symbol) {
			this.definitions.add(symbol);
		}

		public void addUse(Symbol symbol) {
			this.uses.add(symbol);
		}

		public void addInstruction(TextInstruction instruction) {
			this.textInstructions.add(instruction);

			this.length++;
			this.endLocation = this.startLocation + this.length - 1;
		}

		public Symbol getDefinitionsSymbol(String symbolName) {
			for (Symbol curr : this.definitions)
				if (curr.symbol.equals(symbolName))
					return curr;

			return null;
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append("Start: " + this.startLocation + "\n");
			sb.append("End: " + this.endLocation + "\n");
			sb.append("Length: " + this.length + "\n");

			sb.append("Defs: " + this.definitions.size() + "\n");
			for (Symbol def : this.definitions)
				sb.append("\t" + def + "\n");

			sb.append("Uses: " + this.uses.size() + "\n");
			for (Symbol use : this.uses)
				sb.append("\t" + use + "\n");

			sb.append("Text: " + this.textInstructions.size() + "\n");
			for (TextInstruction instr : this.textInstructions)
				sb.append("\t" + instr.classification + ": " + instr + "\n");

			return sb.toString();
		}
	}

	class DescriptiveItem<T> {
		public T item;
		public String errorMsg;

		public DescriptiveItem(T item, String errorMsg) {
			this.item = item;
			this.errorMsg = errorMsg;
		}
	}

	private int machineMemorySize = 600;

	private ArrayList<Module> modules = new ArrayList<Module>();
	private TreeMap<String, DescriptiveItem<Symbol>> symbolTable = new TreeMap<String, DescriptiveItem<Symbol>>();
	private ArrayList<DescriptiveItem<Integer>> memoryMap = new ArrayList<DescriptiveItem<Integer>>();

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
			// (the number right before the definitions, uses, instructions...)
			else {
				// Start a new module when the next type is DEFINITIONS
				// regardless of whether there are actual definitions or not
				if (nextType == Data.DEFINITIONS) {
					this.analyzeLastModule();
					this.initializeModule();
				}

				// Check whatever nextType is and update that value
				int numNewElements = Integer.parseInt(token);
				// Nothing to see here... move along
				if (numNewElements == 0)
					nextType = Data.getNext();

				// Definitions come in groups of two (symbol + location)
				if (nextType == Data.DEFINITIONS)
					remainingDefinitions = numNewElements * 2;

				else if (nextType == Data.USES)
					remainingUses = numNewElements;

				// Instructions come in groups of two (type + word)
				else if (nextType == Data.INSTRUCTIONS)
					remainingInstructions = numNewElements * 2;
			}
		}

		// After iterating through all of the elements, make sure
		// that the last module is analyzed
		this.analyzeLastModule();

		// Perform second pass of the two-part linking process
		this.performSecondPass();
	}

	private void processDefinition(String element, Data partType) {

		if (partType == Data.SYMBOL) {
			this.tempSymbol = new Symbol(element);

		} else if (partType == Data.LOCATION) {
			String symbol = this.tempSymbol.symbol;
			int location = Integer.parseInt(element);

			// Full symbol
			this.tempSymbol = new Symbol(symbol, location);

			// Add it to the modules
			this.currModule.addDefinition(this.tempSymbol);

			// Don't need this anymore
			this.tempSymbol = null;
		}

	}

	private void processUse(String element) {

		this.currModule.addUse(new Symbol(element));

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
			this.tempInstruction = new TextInstruction(classification, opcode,
					address);

			// Add it to the modules
			this.currModule.addInstruction(this.tempInstruction);

			// Don't need this anymore
			this.tempInstruction = null;
		}

	}

	private void initializeModule() {

		// Get its start location on memory
		// Equivalent to the final word of the previous module + 1
		// (unless there are no modules; in that case, it's 0)
		int startLocation = 0;

		if (!this.modules.isEmpty()) {
			int lastModuleIndex = this.modules.size() - 1;
			startLocation = this.modules.get(lastModuleIndex).endLocation + 1;
		}

		this.currModule = new Module(startLocation);
		// Add this to the global list of modules
		// (it will be updated elsewhere through the reference to
		// this.currModule)
		this.modules.add(this.currModule);

	}

	private void analyzeLastModule() {

		// Assuming that modules have been analyzed (that this isn't the
		// start of the program), analyzes the last module by:
		// 1. Pushing the defined variables to a dedicated global list
		// 2. Error detection and arbitrary limits analysis

		if (this.modules.isEmpty())
			return;

		Module lastModule = this.modules.get(this.modules.size() - 1);

		this.setAbsoluteSymbolValues(lastModule);

	}

	private void setAbsoluteSymbolValues(Module module) {

		List<Symbol> definedSymbols = module.definitions;

		DescriptiveItem<Symbol> descriptiveSymbol;
		String errorMsg;
		int absoluteLoc;
		for (Symbol currSymbol : definedSymbols) {
			errorMsg = null;

			if (this.symbolTable.containsKey(currSymbol.symbol)) {
				this.symbolTable.get(currSymbol.symbol).errorMsg = "Error: This variable is multiply defined; first value used.";
				continue;
			}

			// Symbol defined without a relative location
			// (this edge case wasn't even considered, but just in case)
			if (currSymbol.location == null)
				currSymbol.location = 0;

			absoluteLoc = currSymbol.location + module.startLocation;
			currSymbol.location = absoluteLoc;
			currSymbol.moduleNumber = this.modules.size();

			// Update this.symbols (the global structure with the symbols)
			descriptiveSymbol = new DescriptiveItem<Symbol>(currSymbol,
					errorMsg);
			this.symbolTable.put(descriptiveSymbol.item.symbol,
					descriptiveSymbol);
		}

	}

	private void performSecondPass() {

		Symbol relevantSymbol;
		String relevantSymbolName;
		String errorMsg;
		Integer word;
		Integer relativeAddress;
		Integer absoluteAddress;
		for (Module module : this.modules) {
			for (TextInstruction instr : module.textInstructions) {

				errorMsg = null;
				relativeAddress = instr.address;
				absoluteAddress = instr.address;

				if (instr.classification == 'R') {
					absoluteAddress = relativeAddress + module.startLocation;

					if (relativeAddress > module.length) {
						// Even if the check is for the relative address, the
						// final address should be 0; that's why this sets
						// absoluteAddress
						errorMsg = "Error: Relative address exceeds module size; zero used.";
						absoluteAddress = 0;
					}

				} else if (instr.classification == 'E') {
					if (module.uses.size() <= relativeAddress) {
						errorMsg = "Error: External address exceeds length of use list; treated as immediate.";

					} else {
						// Map the address to the external symbol
						relevantSymbolName = module.uses.get(relativeAddress).symbol;

						// Globally defined symbol
						relevantSymbol = this.symbolTable
								.get(relevantSymbolName).item;

						if (relevantSymbol == null) {
							// Check if the symbol is globally defined
							errorMsg = "Error: " + relevantSymbolName
									+ " is not defined; zero used.";
							instr.address = 0;

						} else {
							// Mark the symbol as used in the instruction list
							relevantSymbol.inInstructionList = true;

							// Get its absolute address
							absoluteAddress = this.symbolTable
									.get(relevantSymbolName).item.location;
						}
					}
				}

				if (instr.classification != 'I') {
					// Immediate addresses are often not really addresses
					// (just numbers)
					if (absoluteAddress >= this.machineMemorySize) {
						errorMsg = "Error: Absolute address exceeds machine size; zero used.";
						absoluteAddress = 0;
					}
				}

				word = instr.opcode * 1000 + absoluteAddress;
				// Add the word to the global map
				this.memoryMap
						.add(new DescriptiveItem<Integer>(word, errorMsg));
			}
		}

		// Check if definitions are used in their modules
		Symbol currSymbol;
		Module currModule;
		for (Entry<String, DescriptiveItem<Symbol>> entry : this.symbolTable
				.entrySet()) {

			currSymbol = entry.getValue().item;
			currModule = this.modules.get(currSymbol.moduleNumber - 1);

			for (Symbol currUse : currModule.uses) {
				if (currSymbol.equals(currUse)) {
					currSymbol.inModuleUseList = true;
					break;
				}
			}
		}

		// Print the results
		this.displayResults();
	}

	private void displayResults() {

		System.out.println("Symbol Table");

		DescriptiveItem<Symbol> currEntry;
		for (Entry<String, DescriptiveItem<Symbol>> entry : this.symbolTable
				.entrySet()) {
			currEntry = entry.getValue();
			System.out.print(currEntry.item.symbol + "="
					+ currEntry.item.location);

			if (currEntry.errorMsg != null)
				System.out.print(" " + currEntry.errorMsg);

			System.out.println();
		}

		System.out.println();

		System.out.println("Memory Map");

		int counter = 0;
		int address;
		for (DescriptiveItem<Integer> memoryEntry : this.memoryMap) {

			address = memoryEntry.item;
			System.out.printf("%-3s %s", counter + ":", address);

			if (memoryEntry.errorMsg != null)
				System.out.print(" " + memoryEntry.errorMsg);

			System.out.println();
			counter++;
		}

		System.out.println();

		// for (Entry<String, DescriptiveItem<Symbol>> entry : this.symbolTable
		// .entrySet()) {
		//
		// Symbol currSymbol = entry.getValue().item;
		// System.out.println(currSymbol);
		// System.out.println();
		//
		// }

		Symbol currSymbol;
		for (Entry<String, DescriptiveItem<Symbol>> entry : this.symbolTable
				.entrySet()) {

			currSymbol = entry.getValue().item;

			// It's in a use list... but is it in the instructions?
			if (currSymbol.inInstructionList == false) {
				System.out.println("Warning: " + currSymbol.symbol
						+ " was defined in module " + currSymbol.moduleNumber
						+ " but never used.");
			}
		}

	}

	public static void main(String[] args) throws IOException {

		// Get the file path from the command line
		String filePath;
		if (args.length > 0)
			filePath = args[0];
		else
			filePath = "inputs/input-7.txt";

		new TwoPass(filePath);

	}

}
