# Player2NPC: A Demonstration of the PlayerEngine Framework

[![Player2 AI Game Jam](https://img.shields.io/badge/Player2-AI_Game_Jam-blueviolet)](https://itch.io/jam/ai-npc-jam)
[![Powered by PlayerEngine](https://img.shields.io/badge/Powered%20by-PlayerEngine-orange)](https://github.com/Ladysnake/Automatone/tree/1.20)

Welcome to Player2NPC. This is not just another companion mod; it is a live showcase of the **PlayerEngine** framework, built to fundamentally change our perception of AI NPCs in Minecraft.

This mod was developed by **Goodbird** exclusively for the **Player2 AI Game Jam**, serving as a prime example of how effortlessly developers can create truly embodied and interactive AI agents when equipped with the right tools.

## What Does This Mod Do?

Player2NPC allows you to summon AI companions into your world, driven by the **Player2 API**. Unlike other solutions, our companions are not player-bots running on separate game clients. They are fully independent, server-side entities brought to life by PlayerEngine.

*   **Press 'H'** to open the character selection menu.
*   **Choose a companion** with a unique personality and appearance.
*   **Summon them into your world** and interact with them using natural language in chat.
*   **Give them tasks:** ask them to gather resources ("*can you get me 10 wood?*"), attack mobs ("*take care of that zombie!*"), or simply follow you ("*stick with me*").

You will witness an AI agent that doesn't just respond with text but **acts** within the world, using its inventory and interacting with blocks almost like a real player, all driven by the LLM's understanding of your request.

## Under the Hood: The Magic of `AutomatoneEntity.java`

The power of this mod is showcased in a single class: `AutomatoneEntity`. It serves as the perfect example of how simple PlayerEngine makes it to create complex AI agents. This class is not a hack or a workaround; it's an elegant implementation of the framework's core principles.

### 1. Implementing the Core Interfaces

To transform an ordinary mob into a "player," you only need to implement a few interfaces from PlayerEngine. This is the foundation of our approach.

```java
public class AutomatoneEntity extends LivingEntity implements IAutomatone, IInventoryProvider, IInteractionManagerProvider, IHungerManagerProvider {
    // ...
}
```
*   **`IAutomatone`**: A marker that allows PlayerEngine to recognize this entity as a controllable AI agent.
*   **`IInventoryProvider`**: Grants our mob a full, persistent, player-like inventory.
*   **`IInteractionManagerProvider`**: Empowers the mob with the ability to break/place blocks and use items.
*   **`IHungerManagerProvider`**: Provides a hunger system. While ticking its logic is optional, implementing the interface is part of the core design for full player-like capability.

### 2. Simple Initialization in `init()`

In our mob's constructor, we just instantiate the standard implementations provided by PlayerEngine. No complex boilerplate is required.

```java
public void init() {
    // Provide standard implementations from PlayerEngine
    manager = new LivingEntityInteractionManager(this);
    inventory = new LivingEntityInventory(this);
    hungerManager = new LivingEntityHungerManager();
    
    // And most importantly, connect the "brain"
    if (!getWorld().isClient) {
        controller = new AltoClefController(IBaritone.KEY.get(this));
        controller.getAiBridge().setPlayer2GameId(PLAYER2_GAME_ID);
        if (character != null) {
            controller.getAiBridge().sendGreeting(character);
        }
    }
}
```
The `AltoClefController` is the agent's core. It receives high-level commands (interpreted from natural language by the Player2 API) and translates them into concrete actions in the world using Automatone's navigation.

### 3. The Secret Sauce: Modular Capabilities with Cardinal Components

PlayerEngine's true power lies in its modularity, made possible by **Cardinal Components**. Instead of hardcoding player abilities into one entity, we attach them like building blocks. This is where the framework truly shines for modders.

The `Player2NPCComponents.java` class shows how simple this is:

```java
@KeepName
public final class Player2NPCComponents implements EntityComponentInitializer, WorldComponentInitializer {

    @Override
    public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        // ... standard player components

        // This is where the magic happens for our custom mob:
        registry.registerFor(AutomatoneEntity.class, IInteractionController.KEY, EntityInteractionController::new);
        registry.registerFor(AutomatoneEntity.class, ISelectionManager.KEY, SelectionManager::new);
        registry.registerFor(AutomatoneEntity.class, IBaritone.KEY, BaritoneAPI.getProvider().componentFactory());
    }
    // ...
}
```
With these few lines, we are telling the game: *"Any time you see our `AutomatoneEntity`, attach these standard player systems to it."* This approach means **any modder can make their custom mob player-like by simply registering these components for their entity class.** No mixins, no complex inheritance required. It's clean, compatible, and incredibly powerful.

### 4. The AI Control Loop

When you speak to a companion, a seamless process unfolds:
1.  **Player says:** "*Hey, can you get me some logs?*"
2.  **Player2 API (LLM)** processes this natural language and generates a command: `get oak_log 10`
3.  **PlayerEngine's `AltoClefController`** receives this command.
4.  The controller's task system creates and executes a `MineAndCollectTask`.
5.  The **`AutomatoneEntity`** carries out the actions in the world, guided by Automatone's pathfinding.

## Why This is "Beyond an AI Gimmick"

This mod, powered by PlayerEngine, directly addresses the AI Game Jam's core theme:

*   **Integration:** The AI is not a chatbot in a box. It is a systemic part of the game, capable of altering the world and participating in the core gameplay loop of survival and resource gathering.
*   **Guardrails:** PlayerEngine *is* the guardrail. It provides a deterministic, game-logic-based action layer that reliably executes the high-level goals from an LLM.
*   **Creativity:** It empowers other creators. We're not just showing one cool NPC; we're giving the entire community a tool to build their own intelligent companions and adversaries.
*   **Stability:** Built on the shoulders of giants—Baritone and Cardinal Components—PlayerEngine provides a stable and performant foundation for ambitious AI projects.

## Acknowledgements

*   **PlayerEngine & Player2NPC Mod by Goodbird:** The framework and this demonstration were developed by Goodbird.
*   **Player2 API:** For providing the intelligent "brain" that powers our AI companions.
*   **Automatone/Baritone:** For the best-in-class navigation system.
*   **ChatClef:** For the inspiration and foundational task system.
*   A special thanks to **Itsuka** for invaluable guidance with the Player2 API, brainstorming, and rigorous testing.