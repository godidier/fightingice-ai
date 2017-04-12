import gameInterface.AIInterface;
import simulator.Simulator;
import structs.FrameData;
import structs.GameData;
import structs.Key;
import commandcenter.CommandCenter;
import enumerate.State;
import structs.CharacterData;

import com.fuzzylite.Engine;
import com.fuzzylite.FuzzyLite;
import com.fuzzylite.Op;
import com.fuzzylite.rule.Rule;
import com.fuzzylite.rule.RuleBlock;
import com.fuzzylite.term.Trapezoid;
import com.fuzzylite.term.Triangle;
import com.fuzzylite.variable.InputVariable;
import com.fuzzylite.variable.OutputVariable;




public class KatanaAI implements AIInterface {

	
	private Key inputKey;
	private boolean player;
	private FrameData frameData;
	private CommandCenter cc;
	private Simulator simulator;
	private GameData gd;
	
	private int distance;
	private int energy;
	private CharacterData opp;
	private CharacterData me;
	private boolean isGameJustStarted;
	private int xDifference;
	
	// Fuzzy Logic variables
	private Engine fzEngine;
	private InputVariable fzsRange ;
	private OutputVariable fzsAttack ;
	private RuleBlock fzRuleBlock; 
	
	
	
	@Override
	public void close() {
		// TODO Auto-generated method stub

	}

	@Override
	public String getCharacter() {
		// Using Zen as character
		return CHARACTER_ZEN;
	}

	@Override
	public void getInformation(FrameData frameData) {
		this.frameData = frameData;
	}

	@Override
	public int initialize(GameData arg0, boolean player) {
		// Initialize the global variables at the start of the round
		inputKey = new Key();
		this.player = player;
		frameData = new FrameData();
		cc = new CommandCenter();	
		gd = arg0;
		simulator = gd.getSimulator();
		isGameJustStarted = true;
		xDifference = -300;
		
		// Fuzzy Logic Engine initializations
		fzEngine = new Engine();
        fzEngine.setName("attack-range");
        
        fzsRange = new InputVariable();
		fzsRange.setName("Fight_Range");
		fzsRange.setRange(0.000, 150.0);
		fzsRange.addTerm(new Trapezoid("CLOSE", 0.0, 0.0, 25.0, 50.0));
		fzsRange.addTerm(new Trapezoid("MIDDLE", 25.0, 50.0, 75.0, 100.0));
		fzsRange.addTerm(new Trapezoid("LONG", 75.0, 100.0, 150.0, 150.0));
        fzEngine.addInputVariable(fzsRange);
        
        fzsAttack = new OutputVariable();
        fzsAttack.setName("Attack");
        fzsAttack.setRange(0.000, 1.000);
        fzsAttack.setDefaultValue(Double.NaN);
        fzsAttack.addTerm(new Triangle("B", 0.000, 0.250, 0.500)); 
        fzsAttack.addTerm(new Triangle("STAND_FB", 0.250, 0.500, 0.750)); 
        fzsAttack.addTerm(new Triangle("AIR_B", 0.500, 0.750, 1.000)); 
        fzEngine.addOutputVariable(fzsAttack);
        
        fzRuleBlock = new RuleBlock();
        fzRuleBlock.addRule(Rule.parse("if Fight_Range is CLOSE then Attack is B", fzEngine));
        fzRuleBlock.addRule(Rule.parse("if Fight_Range is MIDDLE then Attack is STAND_FB", fzEngine));
        fzRuleBlock.addRule(Rule.parse("if Fight_Range is LONG then Attack is AIR_B", fzEngine));
        fzEngine.addRuleBlock(fzRuleBlock);

        fzEngine.configure("", "", "Minimum", "Maximum", "Centroid");
        
        
		return 0;
	}

	
	
	
	@Override
	public Key input() {
		return inputKey;
	}

	
	
	
	@Override
	public void processing() {
		// First we check whether we are at the end of the round
		if(!frameData.getEmptyFlag() && frameData.getRemainingTimeMilliseconds() >0){
			// Simulate the delay and look ahead 2 frames. The simulator class exists already in FightingICE
			if (!isGameJustStarted)
				frameData = simulator.simulate(frameData, this.player, null, null, 17);
			else
				isGameJustStarted = false; //if the game just started, no point on simulating
			cc.setFrameData(frameData, player);
			distance = cc.getDistanceX();
			energy = frameData.getMyCharacter(player).getEnergy();
			me = cc.getMyCharacter();
			opp = cc.getEnemyCharacter();
			xDifference = me.getX() - opp.getX();
			
			if (cc.getSkillFlag()) {
				// If there is a previous "command" still in execution, then keep doing it
				inputKey = cc.getSkillKey();
			}
			else {
				// We empty the keys and cancel skill just in case
				inputKey.empty(); 
				cc.skillCancel();
				// Following is the brain of the reflex agent. It determines distance to the enemy
				// and the energy of our agent and then it performs an action
				if ((opp.energy >= 300) && ((me.hp - opp.hp) <= 300))
					cc.commandCall("FOR_JUMP _B B B");
					// if the opp has 300 of energy, it is dangerous, so better jump!!
					// if the health difference is high we are dominating so we are fearless :)
				else if (!me.state.equals(State.AIR) && !me.state.equals(State.DOWN)) { //if not in air
					if ((distance > 150)) {
						cc.commandCall("FOR_JUMP"); //If its too far, then jump to get closer fast
					}
					else if (energy >= 300)
						cc.commandCall("STAND_D_DF_FC"); //High energy projectile
					else if ((distance > 100) && (energy >= 50))
						cc.commandCall("STAND_D_DB_BB"); //Perform a slide kick
					else if (opp.state.equals(State.AIR)) //if enemy on Air
						cc.commandCall("STAND_F_D_DFA"); //Perform a big punch
					else if (distance > 100)
						cc.commandCall("6 6 6"); // Perform a quick dash to get closer
					else{
						cc.commandCall(fuzzyAttack(distance)); //Perform an attack based on the distance evaluated with fuzzy logic
					}
				}
				else if ((distance <= 150) && (me.state.equals(State.AIR) || me.state.equals(State.DOWN)) 
						&& (((gd.getStageXMax() - me.getX())>=200) || (xDifference > 0))
						&& ((me.getX() >=200) || xDifference < 0)) { //Conditions to handle game corners
					if (energy >= 5){ 
						cc.commandCall("AIR_DB"); // Perform air down kick when in air
					}
					else{
						cc.commandCall(fuzzyAttack(distance)); //Perform an attack based on the distance evaluated with fuzzy logic
					}
				}	
				else{
					cc.commandCall(fuzzyAttack(distance)); //Perform an attack based on the distance evaluated with fuzzy logic
				}
			}
		}
		else isGameJustStarted = true;
	}
	
	
	
	
	/**
	 * 
	 * @return the executed attack command
	 */
	private String fuzzyAttack(int distance){
		String attackCommand = "B"; // Default to kick
		
        try{
	        StringBuilder status = new StringBuilder();
	        if (!fzEngine.isReady(status)) {
	            throw new RuntimeException("Engine not ready. "
	                    + "The following errors were encountered:\n" + status.toString());
	        }
	        
	        double dist = fzsRange.getMinimum() + distance * (fzsRange.range() / gd.getStageXMax());
	        fzsRange.setInputValue(dist);
	        fzEngine.process();
	        //System.out.println("===>gd.getStageXMax() ="+gd.getStageXMax()+", distance = "+ distance +", dist = "+ Op.str(dist) +", Output value is = "+ Op.str(fzsAttack.getOutputValue()));
	        FuzzyLite.logger().info(String.format(
	                "Fight_Range.input = %s -> Attack.output = %s",
	                Op.str(dist), Op.str(fzsAttack.getOutputValue())));
	        
	        double res = fzsAttack.getOutputValue();
	        if((0.0<=res) && (res<0.25)){
	        	attackCommand = "B";
	        }
	        else if ((0.25<=res) && (res<0.50)){
	        	attackCommand = "STAND_FB";
	        }
	        else if ((0.50<=res) && (res<0.75)){
	        	attackCommand = "AIR_B";
	        }
	        else if ((0.75<=res) && (res<=1.0)){
	        	attackCommand = "AIR_B";
	        }
	        //System.out.println("===>OutputValue = "+ res +", ATTACK = "+ attackCommand );
        }catch(Exception ex){
        	System.out.println(ex);
        }
        return attackCommand;
    }

}
