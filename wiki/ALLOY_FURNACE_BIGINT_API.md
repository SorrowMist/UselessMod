# Multiblock Alloy Furnace BigInteger Dispatch API Guide

<p>
    <a href="ALLOY_FURNACE_BIGINT_API.md">English</a> |
    <a href="ALLOY_FURNACE_BIGINT_API_ZH_CN.md">简体中文</a>
</p>

This document is for addon mods that need to dispatch crafting batches **larger than `long`** to the
multiblock Omniversal Alloy Furnace. These APIs replace reflective access to internal classes such as
`MultiblockAlloyFurnaceCoreBlockEntity` and `AdvancedAlloyFurnaceAeManager`.

This document corresponds to the current source of the NeoForge 1.21.1 branch.

## Overview

AE2's native `ICraftingProvider#pushPattern` can only express amounts as `long`; a single physical
submission carries at most `Long.MAX / amountPerCraft` crafts. There are only two ways to dispatch
genuinely large numbers (say `1e22` items):

1. **The long "counted" path** — dispatch one `long` window per tick and accumulate to a bigint total
   across many dispatches. This is the interface-forced fallback: slow, and it consumes scheduling.
2. **The native bigint path** (this API) — a single dispatch can deliver a batch **larger than `long`**.

This API turns option 2 into Useless Mod's own public contract, so you do **not** need to depend on
Data Energistics, and you do **not** need to write mixins into AE2's crafting CPU logic.

> **Scope**: only the **multiblock Omniversal Alloy Furnace** (ME pattern assembly + multiblock core).
> The single-block advanced alloy furnace does not support bigint batches and will not appear in
> `findTargets`.

## Table of contents

- [Package layout](#package-layout)
- [General constraints](#general-constraints)
- [1. Discovering machines](#1-discovering-machines)
- [2. Querying capacity](#2-querying-capacity)
- [3. Admission and commit](#3-admission-and-commit)
- [4. Receiving output callbacks (CPU side)](#4-receiving-output-callbacks-cpu-side)
- [5. Complete example](#5-complete-example)
- [FAQ](#faq)
- [Relationship with Data Energistics](#relationship-with-data-energistics)

## Package layout

| Package | Purpose |
| --- | --- |
| `com.sorrowmist.useless.api.crafting.bigint` | Provider-side contract: discovery, capacity, admission, commit |
| `com.sorrowmist.useless.api.crafting.bigint.cpu` | CPU-side SPI: register an adapter, receive output and terminal callbacks |
| `com.sorrowmist.useless.api.crafting.bigint.example` | Reference implementation (not active; copy from it) |

| Type | Purpose |
| --- | --- |
| `AlloyFurnaceBigIntegerApi` | Discovery entry point |
| `AlloyFurnaceBigIntegerTarget` | One machine: query capacity / request a batch |
| `AlloyFurnaceBigIntegerCapacity` | Capacity query result |
| `AlloyFurnaceBigIntegerBatch` | One-shot admission ticket |
| `AlloyFurnaceBigIntegerOutput` | A BigInteger-scale output |
| `AlloyFurnaceBigIntegerCpuAdapter` | The callback interface you implement |
| `AlloyFurnaceBigIntegerCpuAdapters` | Explicit registry |
| `AlloyFurnaceBigIntegerCpuBinding` | Binds your adapter id and context to a batch |
| `AlloyFurnaceBigIntegerBatchContext` | Batch context carried by every callback |
| `AlloyFurnaceBigIntegerBatchResult` | Terminal batch state |

## General constraints

1. **Server thread only.** This applies to every method and every callback.
2. **Do not cache `AlloyFurnaceBigIntegerTarget`.** It is owned by the machine's block entity; caching it
   across ticks turns into a dangling reference once the machine is broken or unloaded. Just call
   `findTargets` again each tick — it is only a grid node walk, very cheap.
3. **Do not reflect on or cast to this mod's block entity classes.** To test "is this a bigint
   provider", use `instanceof AlloyFurnaceBigIntegerProvider`.
4. **The prototype is the amount for a *single* push.** The remaining `count - 1` copies of material are
   withdrawn by **your own ledger**, and you must roll that back if the commit fails.
   See [section 3](#3-admission-and-commit).
5. **Never multiply `count` into the prototype.** The count travels only through the `count` argument.
6. **Do not throw from callbacks**, and do not call `admit`/`commit` from them (re-entrancy). Thrown
   exceptions are logged and swallowed.

## 1. Discovering machines

```java
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerApi;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;

IGrid grid = /* the grid your CPU is on */;
for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
    String machine = target.machineIdentity(); // e.g. minecraft:overworld@128,64,-256
    // ...
}
```

- `findTargets(IGrid)` returns every target **currently available** on the grid, de-duplicated by
  `machineIdentity()` (one physical machine appears only once).
- `findTarget(IGrid, String machineIdentity)` looks up a machine by identity; useful for
  "which machine did I use last time".
- `acceptedPatternKinds()` returns the set of pattern item ids the machine accepts, for a cheap filter
  before expensive decoding.

> **Implementation note**: AE2 19.2.17's `ICraftingService` has no public "enumerate all providers" API
> (only `getCraftingFor(AEKey)` and `getCpus()`), so this API walks `IGrid#getNodes()` +
> `IGridNode#getOwner()`.

## 2. Querying capacity

```java
AlloyFurnaceBigIntegerCapacity capacity =
        target.capacity(pattern, unitPrototype, requestedCount);

if (!capacity.isAvailable()) {
    String reason = capacity.statusKey(); // a translation key you can show to players; may be empty
    return;
}
BigInteger accepted = capacity.accepted(); // may be less than requestedCount, or far beyond long
```

Capacity is the minimum of up to four gates:

| Gate | Meaning |
| --- | --- |
| Recipe availability | Whether the coil tier is high enough and required molds are present (**omniversal patterns only**) |
| Material window | Thread count N ⇒ this batch absorbs at most N × (`Long.MAX` of every material) |
| Output segmentation budget | A single batch produces at most **~131072 `Long.MAX` chunks** (independent of thread count) — prevents an enormous batch from taking tens of seconds to deliver |
| **Energy** | `count × per-craft energy` must be affordable (**omniversal patterns only**) |

**AE2 crafting patterns only have the "material window + output segmentation" gates**: they are folded
after a single assembly on the virtual 3×3 grid, with no bound recipe and no energy cost, so their
capacity is usually far larger than an omniversal pattern's.

### Dynamic throttling: `isThrottled()`

Every bigint path in this mod uses a dynamic "time budget per tick" throttle (modelled on Data
Energistics' submission budget): when measured cost exceeds the budget, subsequent batches are narrowed
proportionally. The granularity is **global** — all machines run on the same server thread, so cost
from multiple machines accumulates and pushes the throttle down together.

```java
if (target.isThrottled() && !urgent) {
    return; // the machine is busy; defer non-urgent batches
}
```

Its purpose is to let you **distinguish** two kinds of "small capacity":

| Situation | Meaning |
| --- | --- |
| Small capacity + `isThrottled() == false` | This is simply the machine's capability (material window / energy / segmentation / configured threads) |
| Small capacity + `isThrottled() == true` | The machine (or another machine on the same server thread) is busy |

**Throttling never drives capacity to 0**: reporting "no capacity" triggers a
"no capacity → resubmit" spin loop on the scheduler side, so throttling only narrows the batch size.
Whether to stop is your call.

> **Output return is subject to the same dynamic throttle, but with a different policy.** Admission
> throttling narrows the **batch size** (accept less new work); return throttling narrows the
> **time willing to be spent per tick** (deliver fewer already-finished outputs). Their floors also
> differ: admission **must** accept at least 1 craft (otherwise the spin loop above), while the return
> path may truncate to very little (it is idempotent; the remainder stays queued for the next tick) —
> but it still keeps a floor, so that sustained load cannot starve the queue and let backpressure push
> admission to 0. The visible consequence for you: **the busier the server, the later
> `onBatchOutputs` arrives** — but it always arrives.

**`capacity` has no side effects** and may be called every tick.

### A single batch's size is machine-adaptive (AIMD); capacity never drops to zero from backlog

The machine uses a **congestion-control-style** adaptive controller to size a single batch (measured in
output chunks):

| Phase | Behaviour |
| --- | --- |
| Slow start | Starts small (1/100 of the ceiling); while the backlog stays under one batch in flight it multiplies by 10 each tick, quickly probing the usable ceiling |
| Multiplicative decrease | As soon as the backlog exceeds "one batch in flight", it halves and exits slow start |
| Linear recovery | Afterwards it steps back up gradually while the backlog stays low, so it cannot flood again the moment there is room |

It converges on "**a batch drains in about one or two ticks**" — the smoothest possible shape.

**What this means for you**:
- `capacity` is **always positive** (unless a hard condition fails: invalid recipe, coil tier too low,
  missing molds, no energy), so the scheduler never has to pause and restart;
- during extremely large crafts `capacity` will float within a range — that is the machine probing the
  right batch size, and it is **normal**;
- do **not** treat `capacity` as a fixed measure of machine capability.

**Why not a fixed value**: the right batch size depends on delivery capacity and on the scheduler's
dispatch rate, and neither can be known at compile time. A fixed value is either too large (the first
batch takes tens of seconds to deliver ⇒ pauses between tasks) or too small (wasted throughput). The
controller converges using measured feedback instead.

## 3. Admission and commit

```java
AlloyFurnaceBigIntegerBatch batch =
        target.admit(pattern, unitPrototype, capacity.accepted(), binding);
if (batch == null) {
    return; // not acceptable right now
}
if (batch.commit(unitPrototype)) {
    // success: input ownership has transferred to the machine
} else {
    // failure: the machine consumed nothing. Roll back your own (count - 1) withdrawal, then try another machine.
}
```

### Call contract (read this fully)

1. **Commit only once.** A second `commit` call throws `IllegalStateException`.
2. **You must commit with the prototype you prepared**: the very same `KeyCounter[]` array object you
   passed to `admit` — no copying, no rebuilding. Committing with another array throws
   `IllegalArgumentException`.
3. **The machine consumes only the one prototype it holds.** The prototype passed to `admit` is the
   material for a **single push**, not `count` copies. The remaining `count - 1` copies are withdrawn by
   **your own BigInteger ledger**. Data Energistics' Trinity Data Core does exactly this: it withdraws
   via `TrinityExactInputTransaction` and rolls back when our commit fails.
4. **A failed commit does not lose material**: when `commit` returns `false` the machine does not clear
   the prototype, so you should roll back your own `count - 1` withdrawal and retry later or try a
   different provider.
5. **A `true` commit transfers ownership**: from then on the material in the prototype belongs to the
   machine (it clears it), and outputs are delivered back to the network by the machine as
   `AlloyFurnaceBigIntegerOutput` / reported to registered adapters.
6. `admit` **consumes no material and charges no energy**; it only performs a snapshot check. The real
   checks run again at `commit` (machine state may change in between — the `commit` return value is
   authoritative).

### How outputs return to the network

AE2's storage interface accepts at most `long` per call, so outputs are split into `Long.MAX_VALUE`
chunks and written back to the ME network **across ticks**. The chunk size matches Data Energistics'
own `PHYSICAL_CHUNK` convention.

If you registered an adapter on the CPU side, you additionally receive the **complete** BigInteger
output callback (see the next section).

## 4. Receiving output callbacks (CPU side)

### 4.1 Implement the adapter

```java
public final class MyCpuAdapter implements AlloyFurnaceBigIntegerCpuAdapter {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("mymod", "bigint_cpu");

    @Override public ResourceLocation id() { return ID; }

    @Override
    public void onBatchAdmitted(AlloyFurnaceBigIntegerBatchContext context) {
        // Ownership has transferred. Record the planned outputs in your ledger / park the job as waiting.
    }

    @Override
    public void onBatchOutputs(AlloyFurnaceBigIntegerBatchContext context,
                               List<AlloyFurnaceBigIntegerOutput> actualOutputs) {
        // All outputs have returned to the network. actualOutputs are exact BigInteger values --
        // for dynamic outputs, these keys are the real ones.
    }

    @Override
    public void onBatchFinished(AlloyFurnaceBigIntegerBatchContext context,
                                AlloyFurnaceBigIntegerBatchResult result) {
        // SUCCESS / CANCELLED / REJECTED
    }
}
```

### 4.2 Registering

Register once during your mod's common init:

```java
@SubscribeEvent
public static void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> AlloyFurnaceBigIntegerCpuAdapters.register(new MyCpuAdapter()));
}
```

The registry is global and process-wide; it is not cleared by save or world reloads. If your mod can be
disabled at runtime, remove it with `AlloyFurnaceBigIntegerCpuAdapters.unregister(id)`.

### 4.3 Callback ordering

| When | Callback |
| --- | --- |
| `commit` succeeds, same tick | `onBatchAdmitted` |
| After **all** outputs have returned to the network | `onBatchOutputs`, immediately followed by `onBatchFinished(SUCCESS)` |
| Machine broken / player cancelled / job aborted | only `onBatchFinished(CANCELLED)` |

**A batch receives exactly one terminal callback** (`SUCCESS` or `CANCELLED`, never both). When
cancelled, even if the outputs happen to be force-flushed within the same tick, no `SUCCESS` follows.

> **Large batches return across multiple ticks.** Outputs are aggregated per key and written back in
> `Long.MAX_VALUE` chunks, and a single machine has a per-tick cap on return work (time budget + per-key
> chunk cap). A `1e22`-scale batch may therefore take a few seconds to fully land, and
> `onBatchOutputs` / `onBatchFinished(SUCCESS)` arrive correspondingly later. **This is deliberate** —
> it prevents one enormous write-back from stalling the server thread. If your job is latency-sensitive,
> design for "the callback arrives late but always arrives" and do not set an aggressive timeout after
> dispatching a batch.

**Cancellation does not lose outputs**: the machine force-writes the remaining queued outputs back to
the ME network, just without the per-tick pacing. On `CANCELLED`, treat it as "the machine delivered
this batch, but I did not get per-item receipts" and do **not** re-record the outputs.

### 4.4 Save reload

`cpuToken` is not serializable and is `null` after a reload; the machine's output queue itself is
persisted as usual (**no items are lost**), but **callbacks are not re-sent**. Use the ME network's
actual balance as the source of truth, or reconcile by `batchId()`.

### 4.5 What happens without an adapter

There is no degradation risk at all: if you register no adapter (or pass `cpu = null` to `admit`),
outputs are still chunked back into the ME network as usual — the callbacks simply are not sent.

## 5. Complete example

`com.sorrowmist.useless.api.crafting.bigint.example.ExampleBigIntegerCpuAdapter` is a reference
implementation you can copy, covering the whole discovery → capacity → admission → commit → output
callback flow. Its dispatch entry point, simplified:

```java
public static BigInteger dispatch(IGrid grid, IPatternDetails pattern,
                                  KeyCounter[] unitPrototype, BigInteger requested,
                                  Object jobHandle, boolean urgent) {
    AlloyFurnaceBigIntegerCpuBinding binding =
            new AlloyFurnaceBigIntegerCpuBinding(MyCpuAdapter.ID, jobHandle);

    for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
        // Defer non-urgent batches when the machine is busy (throttling never drives capacity to 0;
        // whether to stop is your call).
        if (target.isThrottled() && !urgent) {
            continue;
        }
        AlloyFurnaceBigIntegerCapacity capacity = target.capacity(pattern, unitPrototype, requested);
        if (!capacity.isAvailable()) {
            continue;
        }
        AlloyFurnaceBigIntegerBatch batch =
                target.admit(pattern, unitPrototype, capacity.accepted(), binding);
        if (batch == null) {
            continue;
        }
        if (batch.commit(unitPrototype)) {
            return batch.count(); // input ownership transferred
        }
        // commit failed: roll back your own (count - 1) withdrawal, then try the next machine
    }
    return null;
}
```

## FAQ

### Why is capacity much smaller than `long`?

First check the **energy gate**. Energy billing **depends on the coil tier**:

| Coil | Billing | Limits the count? |
| --- | --- | --- |
| **Useful coil (tier 10)** | A single **flat** cost per batch, `ceil(recipeEnergy / 1024)`, **independent of count** | **No** — there is no energy gate |
| Other tiers | `count × per-craft energy` | Yes: `count ≤ available energy / per-craft energy` |

So on the **useful coil, capacity is limited only by the "material window + output segmentation"
budget and can reach the full bigint scale**; lower tiers are narrowed by the internal energy buffer
(its capacity grows with tier; only the top tier is `Long.MAX_VALUE`).

Other things that can shrink capacity: coil tier too low, missing mold hub or molds, the return queue
being saturated by backpressure, or active dynamic throttling (use `isThrottled()` to identify the
last one).

### With millions of coil threads, why is capacity still limited?

Because the **output segmentation budget is independent of the thread count**. The physical reason
chunks exist at all is that "AE2's storage interface accepts at most `long` per call", so 1e24 items
necessarily split into roughly a million chunks — and the return path can only make so many insert
calls per tick. **Delivery capacity does not grow with thread count.**

So a single batch is limited to about 131072 chunks (deliberately above "time budget ÷ per-insert cost",
so that the time budget — not this cap — is the real limiter). Without that limit, a
million-thread coil would produce million-chunk batches taking tens of seconds to deliver — visible as
"a pause between tasks", and once the first batch is oversized, backlog-based grading cannot catch up.

**This does not reduce total throughput**: the total number of chunks is unchanged; a huge batch is
simply split into several batches that can each be delivered promptly. Your million threads still
determine the **material window** (one long window per thread) — they just no longer determine a single
batch's **delivery size**.

### Why does the machine return `waiting_mold_hub` / `waiting_tier`?

`statusKey` is the **full translation key** produced by the machine's recipe-availability check (for
example `gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_tier`); render it with
`Component.translatable(statusKey)`. Common values:

| Key suffix | Meaning |
| --- | --- |
| `waiting_structure` | The multiblock structure is not formed |
| `waiting_recipe` | The recipe bound to the pattern is no longer valid (changed/removed) |
| `waiting_tier` | Coil tier too low |
| `waiting_mold_hub` | No mold hub |
| `waiting_missing_mold` | The mold hub lacks a mold this recipe needs |

### Should I retry after `commit` returns `false`?

Yes, but first roll back your own `count - 1` withdrawal. Common causes: energy was consumed by
something else in the meantime, the return queue is full (backpressure), or the machine was broken /
not formed at that instant.

### How is an omniversal pattern's bigint batch executed?

**Folded**: the machine resolves the bound recipe once, multiplies outputs by `count` (accounted in
BigInteger), then chunks them at `Long.MAX_VALUE` back into the network; energy is charged as
`count × per-craft energy`. It does not actually run `count` separate processing operations.

### Can I dispatch only AE2 crafting patterns?

Yes. AE2 crafting patterns support bigint batches too, and they **cost no energy** (the machine
assembles once on the virtual 3×3 grid and scales the result).

## Relationship with Data Energistics

Data Energistics 3.3.0's Trinity Data Core uses its own `BigIntegerCraftingProviderAdapter`; this mod's
DE adapter and the public API described here **share the same capacity and energy decisions**, so both
entry points compute the same result for the same machine.

If you also use Data Energistics, nothing extra is required — the two paths do not interfere:

- DE's exact branch requires "the target has been exclusively reserved by an async proposal" and only
  fires when that holds;
- this API is called by you directly and is independent of DE's scheduling.

> This mod only publishes a machine identity for pattern kinds that **truly implement bigint
> semantics**. Because DE's exact branch, once it takes over a provider, **never falls back** to the
> long path, "this machine cannot serve right now" must be expressed as **zero capacity** rather than
> publishing capacity and rejecting at commit. That is also why `capacity` returns 0 when energy or
> molds are missing.

### Does Data Energistics need extra adaptation for omniversal patterns?

**No.** This is something this mod decides on its own; no DE-side change is required. Every gate of
DE's exact branch is driven by **the identity the provider publishes** — DE does not inspect the
pattern kind:

| DE-side step | Source |
| --- | --- |
| `ProviderCapacitySnapshot.machineTargetId` | Taken directly from the `CountedCraftingTarget.machineIdentity()` returned by `captureCapacityFast` |
| exact adapter lookup | The adapter registered through the DE entrypoint (which implements `BigIntegerCraftingProviderAdapter`) |
| the target passed back to `prepareBigIntegerBatch` | Rebuilt verbatim from the route / machine identity strings above |

In other words, as long as this mod publishes `TARGETED` capacity carrying a machine identity for
omniversal patterns, DE's `usingSelectedProposal && machineTargetId.isPresent() && exactContext != null`
conditions are satisfied and it enters the exact branch. **Conversely**, if this mod publishes no
machine identity (for example the single-block advanced alloy furnace), DE's `machineTargetId` is empty
and omniversal patterns can only use the long counted path — one `long` window per tick.

### When does it still not reach bigint?

Never for compatibility reasons, but **at runtime** three situations send a given batch back to the
long path. None of them is a defect:

1. **This machine's capacity is 0**: missing molds, tier too low, no energy, or the return queue is
   saturated by backpressure.
2. **DE's async proposal did not pick this machine**: when `usingSelectedProposal` is false, DE falls
   back to synchronous dispatch and does not enter the exact branch this tick (it retries next tick).
3. **DE's own dispatch budget is exhausted**: DE's dispatch window / budget ran out.

Also note that **energy is billed in two places**: DE charges its own power for the CPU, and this
machine charges its own buffer as `count × per-craft energy`. This matches the long path exactly (which
also has "CPU power + machine power") and is not a newly introduced double charge.
