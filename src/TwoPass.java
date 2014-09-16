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

	private enum DataKind {
		// Items found in module
		DEFINITIONS, USES, INSTRUCTIONS,
		// Definition sub-categories
		SYMBOL, LOCATION,
		// Instruction sub-categories
		TYPE, WORD;

		private static DataKind current = getFirst();

		public static DataKind getFirst() {
			return current = DataKind.DEFINITIONS;
		}

		public static DataKind getNext() {
			if (current == DataKind.DEFINITIONS)
				return current = DataKind.USES;

			else if (current == DataKind.USES)
				return current = DataKind.INSTRUCTIONS;

			else
				return current = DataKind.DEFINITIONS;
		}
	}

	private class Symbol {
		private String symbol;
		private Integer location;

		// Starting from 1
		private Integer moduleNumber;
		// True if it appears on the text
		private boolean usedSomewhere;

		public Symbol(String symbol, Integer location) {
			this.symbol = symbol;
			this.location = location;

			this.usedSomewhere = false;
		}

		public Symbol(String symbol) {
			this(symbol, null);
		}

		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(this.symbol + "=" + this.location + "\n");
			sb.append("module number = " + this.moduleNumber + "\n");

			return sb.toString();
		}
	}

	private class TextInstruction {
		private Character classification;
		private Integer opcode;
		private Integer address;

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

	private class Module {
		private int startLocation;
		private int endLocation;
		private int length;

		public List<Symbol> definitions;
		public List<Symbol> uses;
		public List<TextInstruction> textInstructions;

		// Used to determine if variables in a use list are present in the text
		public List<String> unusedInTextSymbols;

		public Module(int startLocation) {
			this.startLocation = startLocation;
			this.endLocation = startLocation;
			this.length = 0;

			this.definitions = new ArrayList<Symbol>();
			this.uses = new ArrayList<Symbol>();
			this.textInstructions = new ArrayList<TextInstruction>();

			this.unusedInTextSymbols = new ArrayList<String>();
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

	private class DescriptiveItem<T> {
		private T item;
		private String errorMsg;

		public DescriptiveItem(T item, String errorMsg) {
			this.item = item;
			this.errorMsg = errorMsg;
		}

		public String getErrorMsg() {
			return this.errorMsg;
		}

		public void setErrorMsg(String errorMsg) {
			this.errorMsg = errorMsg;
		}
	}

	private int machineMemorySize = 600;

	private ArrayList<Module> modules = new ArrayList<Module>();
	private TreeMap<String, DescriptiveItem<Symbol>> definedSymbolTable = new TreeMap<String, DescriptiveItem<Symbol>>();
	private ArrayList<DescriptiveItem<Integer>> memoryMap = new ArrayList<DescriptiveItem<Integer>>();

	// Last-visited and incomplete items as the data is being processed
	// (used to hold parts of the symbols / instructions / module across
	// iterations).
	private Symbol tempSymbol;
	private TextInstruction tempInstruction;
	private Module currModule;

	public TwoPass(String inputFilePath) throws FileNotFoundException {
		// Save all of the file content into a variable
		Scanner scanner = new Scanner(new File(inputFilePath));
		String content = scanner.useDelimiter("\\A").next();

		// Identify the tokens using RegEx
		Matcher matcher = Pattern.compile("[\\d\\w]+").matcher(content);

		// Type that will be visited first
		DataKind nextType = DataKind.getFirst();

		int remainingDefinitions = 0;
		int remainingUses = 0;
		int remainingInstructions = 0;

		String token;
		while (matcher.find()) {
			token = matcher.group();

			if (remainingDefinitions > 0) {
				if (remainingDefinitions % 2 == 0)
					this.processDefinition(token, DataKind.SYMBOL);
				else
					this.processDefinition(token, DataKind.LOCATION);

				remainingDefinitions--;
				if (remainingDefinitions == 0)
					nextType = DataKind.getNext();
				continue;
			}

			else if (remainingUses > 0) {
				this.processUse(token);

				remainingUses--;
				if (remainingUses == 0)
					nextType = DataKind.getNext();
				continue;
			}

			else if (remainingInstructions > 0) {
				if (remainingInstructions % 2 == 0)
					this.processInstruction(token, DataKind.TYPE);
				else
					this.processInstruction(token, DataKind.WORD);

				remainingInstructions--;
				if (remainingInstructions == 0)
					nextType = DataKind.getNext();
				continue;
			}

			// Only case left: a "remaining" counter
			// (the number right before the definitions, uses, instructions...)
			else {
				// Start a new module when the next type is DEFINITIONS
				// regardless of whether there are actual definitions or not
				if (nextType == DataKind.DEFINITIONS) {
					this.analyzeLastModule();
					this.initializeModule();
				}

				// Check whatever nextType is and update that value
				int numNewElements = Integer.parseInt(token);
				// Nothing to see here... move along
				if (numNewElements == 0)
					nextType = DataKind.getNext();

				// Definitions come in groups of two (symbol + location)
				if (nextType == DataKind.DEFINITIONS)
					remainingDefinitions = numNewElements * 2;

				// Uses come one at a time
				else if (nextType == DataKind.USES)
					remainingUses = numNewElements;

				// Instructions come in groups of two (type + word)
				else if (nextType == DataKind.INSTRUCTIONS)
					remainingInstructions = numNewElements * 2;
			}
		}

		// After iterating through all of the elements, make sure
		// that the last module is analyzed
		this.analyzeLastModule();

		// Perform second pass of the two-part linking process
		this.performSecondPass();
	}

	private void initializeModule() {

		// Get its starting location on memory, which is equivalent to the final
		// word of the previous module + 1 (if there are no modules, it's 0)
		int startLocation = 0;

		if (!this.modules.isEmpty()) {
			int lastModuleIndex = this.modules.size() - 1;
			startLocation = this.modules.get(lastModuleIndex).endLocation + 1;
		}

		this.currModule = new Module(startLocation);
		// Add it to the global list of modules (it will be updated elsewhere
		// through the reference to this.currModule)
		this.modules.add(this.currModule);

	}

	private void analyzeLastModule() {

		// Assuming that modules have been analyzed (that this isn't the
		// start of the program) analyzes the last module by pushing the defined
		// variables to a dedicated global structure

		if (this.modules.isEmpty())
			return;

		Module lastModule = this.modules.get(this.modules.size() - 1);

		this.setAbsoluteSymbolValues(lastModule);

	}

	private void processDefinition(String element, DataKind partType) {

		if (partType == DataKind.SYMBOL) {
			// Save it onto the temporary variable (it will be seen again when
			// interpreting the LOCATION part of a symbol)
			this.tempSymbol = new Symbol(element);

		} else if (partType == DataKind.LOCATION) {
			String symbol = this.tempSymbol.symbol;
			int location = Integer.parseInt(element);

			// Assemble the full symbol and add it to the modules!
			this.tempSymbol = new Symbol(symbol, location);
			this.currModule.addDefinition(this.tempSymbol);
		}

	}

	private void processUse(String element) {

		this.currModule.addUse(new Symbol(element));

	}

	private void processInstruction(String element, DataKind partType) {

		if (partType == DataKind.TYPE) {
			char classification = element.charAt(0);
			this.tempInstruction = new TextInstruction(classification);

		} else if (partType == DataKind.WORD) {
			char classification = this.tempInstruction.classification;
			int opcode = Character.getNumericValue(element.charAt(0));
			int address = Integer.parseInt(element.substring(1));

			// Full instruction
			this.tempInstruction = new TextInstruction(classification, opcode,
					address);

			// Add it to the modules
			this.currModule.addInstruction(this.tempInstruction);
		}

	}

	private void setAbsoluteSymbolValues(Module module) {

		List<Symbol> definedSymbols = module.definitions;

		DescriptiveItem<Symbol> itemSymbolTable;
		String errorMsg;
		Integer absoluteLoc;
		for (Symbol currSymbol : definedSymbols) {
			errorMsg = null;

			itemSymbolTable = this.definedSymbolTable.get(currSymbol.symbol);
			if (itemSymbolTable != null) {
				itemSymbolTable
						.setErrorMsg("Error: This variable is multiply defined; first value used.");
				continue;
			}

			// In case symbol is defined without a relative location
			if (currSymbol.location == null)
				currSymbol.location = 0;

			absoluteLoc = currSymbol.location + module.startLocation;
			currSymbol.location = absoluteLoc;
			currSymbol.moduleNumber = this.modules.size();

			// Update the global structure with the symbols
			itemSymbolTable = new DescriptiveItem<Symbol>(currSymbol, errorMsg);
			this.definedSymbolTable.put(itemSymbolTable.item.symbol,
					itemSymbolTable);
		}

	}

	private void performSecondPass() {

		DescriptiveItem<Symbol> itemSymbolTable;
		Symbol symbol;
		String symbolName;
		String errorMsg;
		Integer word;
		Integer relativeAddress;
		Integer absoluteAddress;
		for (Module module : this.modules) {

			// In order to verify that the variables in a module's use list are
			// in the text, add them all to a structure and later remove them
			// from it as they are found in the text.
			for (Symbol use : module.uses)
				if (this.definedSymbolTable.containsKey(use.symbol))
					module.unusedInTextSymbols.add(use.symbol);

			for (TextInstruction instr : module.textInstructions) {

				errorMsg = null;
				relativeAddress = instr.address;
				absoluteAddress = instr.address;

				if (instr.classification == 'R') {
					absoluteAddress = relativeAddress + module.startLocation;

					if (relativeAddress > module.length) {
						errorMsg = "Error: Relative address exceeds module size; zero used.";
						absoluteAddress = 0;
					}

				} else if (instr.classification == 'E') {
					if (module.uses.size() <= relativeAddress) {
						errorMsg = "Error: External address exceeds length of use list; treated as immediate.";

					} else {
						// Map the address to the external symbol
						symbolName = module.uses.get(relativeAddress).symbol;
						itemSymbolTable = this.definedSymbolTable
								.get(symbolName);

						if (itemSymbolTable == null) {
							errorMsg = "Error: " + symbolName
									+ " is not defined; zero used.";
							instr.address = 0;

						} else {
							// Mark the symbols as "used" in the test if it's
							// defined, which is the same as removing the
							// "unused" mark from them. If the symbol was not
							// defined (not in this.definedSymbolTable), don't
							// do anything.
							module.unusedInTextSymbols.remove(symbolName);

							symbol = itemSymbolTable.item;

							if (symbol == null) {
								errorMsg = "Error: " + symbolName
										+ " is not defined; zero used.";
								instr.address = 0;

							} else {
								// Mark actually defined symbol as used
								// somewhere in the program
								symbol.usedSomewhere = true;

								// Get its absolute address
								absoluteAddress = this.definedSymbolTable
										.get(symbolName).item.location;
							}
						}
					}
				}

				// This check applies to all instructions but the Immediate ones
				// (immediate addresses are often not really addresses).
				if (instr.classification != 'I') {
					if (absoluteAddress >= this.machineMemorySize) {
						errorMsg = "Error: Absolute address exceeds machine size; zero used.";
						absoluteAddress = 0;
					}
				}

				// Add the formed word to the global memory map
				word = instr.opcode * 1000 + absoluteAddress;
				this.memoryMap
						.add(new DescriptiveItem<Integer>(word, errorMsg));
			}
		}

		// Print the results to the console
		this.displayResults();
	}

	private void displayResults() {

		/* Symbol Table */

		System.out.println("Symbol Table");

		DescriptiveItem<Symbol> currEntry;
		for (Entry<String, DescriptiveItem<Symbol>> entry : this.definedSymbolTable
				.entrySet()) {
			currEntry = entry.getValue();

			System.out.print(currEntry.item.symbol + "="
					+ currEntry.item.location);

			if (currEntry.getErrorMsg() != null)
				System.out.print(" " + currEntry.getErrorMsg());

			System.out.println();
		}

		System.out.println();

		/* Memory Map */

		System.out.println("Memory Map");

		int counter = 0;
		int address;
		for (DescriptiveItem<Integer> memoryEntry : this.memoryMap) {

			address = memoryEntry.item;
			System.out.printf("%-3s %s", counter + ":", address);

			if (memoryEntry.getErrorMsg() != null)
				System.out.print(" " + memoryEntry.getErrorMsg());

			System.out.println();
			counter++;
		}

		System.out.println();

		/* Warnings */

		for (DescriptiveItem<Symbol> descSymbol : this.definedSymbolTable
				.values())
			if (!descSymbol.item.usedSomewhere)
				System.out.println("Warning: " + descSymbol.item.symbol
						+ " was defined in module "
						+ descSymbol.item.moduleNumber + " but never used.");

		int moduleCounter = 1;
		for (Module currModule : this.modules) {

			for (String badSymbol : currModule.unusedInTextSymbols) {
				System.out
						.println("Warning: In module "
								+ moduleCounter
								+ " "
								+ badSymbol
								+ " appeared in the use list but was not actually used.");
			}

			moduleCounter++;
		}

	}

	public static void main(String[] args) throws IOException {

		int inputFile = 2;

		String filePath;
		if (args.length > 0)
			filePath = args[0];
		else
			filePath = "inputs/input-" + inputFile + ".txt";

		new TwoPass(filePath);

	}

}
