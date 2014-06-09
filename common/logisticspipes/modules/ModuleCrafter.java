package logisticspipes.modules;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import logisticspipes.LogisticsPipes;
import logisticspipes.api.IRoutedPowerProvider;
import logisticspipes.blocks.crafting.LogisticsCraftingTableTileEntity;
import logisticspipes.interfaces.IHUDModuleHandler;
import logisticspipes.interfaces.IHUDModuleRenderer;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.interfaces.IModuleWatchReciver;
import logisticspipes.interfaces.ISendRoutedItem;
import logisticspipes.interfaces.IWorldProvider;
import logisticspipes.interfaces.routing.ICraftItems;
import logisticspipes.interfaces.routing.IFilter;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.items.ItemUpgrade;
import logisticspipes.logistics.LogisticsManager;
import logisticspipes.logisticspipes.IInventoryProvider;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.IRoutedItem.TransportMode;
import logisticspipes.network.GuiIDs;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractpackets.CoordinatesPacket;
import logisticspipes.network.abstractpackets.ModernPacket;
import logisticspipes.network.packets.block.CraftingPipeNextAdvancedSatellitePacket;
import logisticspipes.network.packets.block.CraftingPipePrevAdvancedSatellitePacket;
import logisticspipes.network.packets.cpipe.CPipeNextSatellite;
import logisticspipes.network.packets.cpipe.CPipePrevSatellite;
import logisticspipes.network.packets.cpipe.CPipeSatelliteId;
import logisticspipes.network.packets.cpipe.CPipeSatelliteImport;
import logisticspipes.network.packets.cpipe.CPipeSatelliteImportBack;
import logisticspipes.network.packets.cpipe.CraftingAdvancedSatelliteId;
import logisticspipes.network.packets.cpipe.CraftingFuzzyFlag;
import logisticspipes.network.packets.cpipe.CraftingPipeOpenConnectedGuiPacket;
import logisticspipes.network.packets.gui.GuiArgument;
import logisticspipes.network.packets.hud.HUDStartWatchingPacket;
import logisticspipes.network.packets.hud.HUDStopWatchingPacket;
import logisticspipes.network.packets.orderer.OrdererManagerContent;
import logisticspipes.network.packets.pipe.CraftingPipePriorityDownPacket;
import logisticspipes.network.packets.pipe.CraftingPipePriorityUpPacket;
import logisticspipes.network.packets.pipe.CraftingPipeStackMovePacket;
import logisticspipes.network.packets.pipe.CraftingPipeUpdatePacket;
import logisticspipes.network.packets.pipe.CraftingPriority;
import logisticspipes.network.packets.pipe.FluidCraftingAdvancedSatelliteId;
import logisticspipes.network.packets.pipe.FluidCraftingAmount;
import logisticspipes.network.packets.pipe.FluidCraftingPipeAdvancedSatelliteNextPacket;
import logisticspipes.network.packets.pipe.FluidCraftingPipeAdvancedSatellitePrevPacket;
import logisticspipes.pipefxhandlers.Particles;
import logisticspipes.pipes.PipeFluidSatellite;
import logisticspipes.pipes.PipeItemsCraftingLogistics;
import logisticspipes.pipes.PipeItemsSatelliteLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.pipes.basic.CoreRoutedPipe.ItemSendMode;
import logisticspipes.pipes.signs.CraftingPipeSign;
import logisticspipes.pipes.upgrades.UpgradeManager;
import logisticspipes.proxy.MainProxy;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.proxy.interfaces.ICraftingRecipeProvider;
import logisticspipes.proxy.interfaces.IFuzzyRecipeProvider;
import logisticspipes.request.CraftingTemplate;
import logisticspipes.request.RequestTreeNode;
import logisticspipes.routing.ExitRoute;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.routing.order.IOrderInfoProvider.RequestType;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.CraftingRequirement;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.SidedInventoryMinecraftAdapter;
import logisticspipes.utils.SinkReply;
import logisticspipes.utils.SinkReply.BufferMode;
import logisticspipes.utils.SinkReply.FixedPriority;
import logisticspipes.utils.WorldUtil;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Icon;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import buildcraft.api.inventory.ISpecialInventory;
import buildcraft.transport.TileGenericPipe;
import cpw.mods.fml.common.network.Player;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ModuleCrafter extends LogisticsGuiModule implements ICraftItems {
	
	private PipeItemsCraftingLogistics _pipe;
	
	private IRequestItems _invRequester;
	private IRoutedPowerProvider _power;
	private ForgeDirection _sneakyDirection = ForgeDirection.UNKNOWN;
	private IWorldProvider _world;


	public int satelliteId = 0;
	public int[] advancedSatelliteIdArray = new int[9];
	public int[] fuzzyCraftingFlagArray = new int[9];
	public int priority = 0;

	
	// from PipeItemsCraftingLogistics
	protected ItemIdentifierInventory _dummyInventory = new ItemIdentifierInventory(11, "Requested items", 127);
	protected ItemIdentifierInventory _liquidInventory = new ItemIdentifierInventory(ItemUpgrade.MAX_LIQUID_CRAFTER, "Fluid items", 1, true);
	
	protected int[] amount = new int[ItemUpgrade.MAX_LIQUID_CRAFTER];
	public int[] liquidSatelliteIdArray = new int[ItemUpgrade.MAX_LIQUID_CRAFTER];
	public int liquidSatelliteId = 0;

	public boolean[] craftingSigns = new boolean[6];
	public boolean waitingForCraft = false;

	public final LinkedList<LogisticsOrder> _extras = new LinkedList<LogisticsOrder>();
	private WeakReference<TileEntity> lastAccessedCrafter = new WeakReference<TileEntity>(null);


	public ModuleCrafter() {
	}

	
	public ModuleCrafter(PipeItemsCraftingLogistics parent) {
		_pipe=parent;
		_invProvider = parent;
		_power=parent;
		_invRequester=parent;
	}
	
	/** 
	 * assumes that the invProvider is also IRequest items.
	 */
	@Override
	public void registerHandler(IInventoryProvider invProvider, IWorldProvider world, IRoutedPowerProvider powerprovider) {
		_invProvider = invProvider;
		_power=powerprovider;	
		_invRequester=(IRequestItems)_invProvider;
	}
	
	
	protected static final SinkReply	_sinkReply	= new SinkReply(FixedPriority.ItemSink, 0, true, false, 1, 0);
	
	@Override
	public SinkReply sinksItem(ItemIdentifier item, int bestPriority, int bestCustomPriority, boolean allowDefault, boolean includeInTransit) {
		if(bestPriority > _sinkReply.fixedPriority.ordinal() || (bestPriority == _sinkReply.fixedPriority.ordinal() && bestCustomPriority >= _sinkReply.customPriority)) return null;
		return new SinkReply(_sinkReply, spaceFor(item, includeInTransit));
	}
	
	protected int spaceFor(ItemIdentifier item, boolean includeInTransit) {
		int count = 0;
		WorldUtil wUtil = new WorldUtil(_invProvider.getWorld(), _invProvider.getX(), _invProvider.getY(), _invProvider.getZ());
		for(AdjacentTile tile: wUtil.getAdjacentTileEntities(true)) {
			if(!(tile.tile instanceof IInventory)) continue;
			if(tile.tile instanceof TileGenericPipe) continue;
			IInventory base = (IInventory)tile.tile;
			if(base instanceof net.minecraft.inventory.ISidedInventory) {
				base = new SidedInventoryMinecraftAdapter((net.minecraft.inventory.ISidedInventory)base, tile.orientation.getOpposite(), false);
			}
			IInventoryUtil inv = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(base);
			count += inv.roomForItem(item, 9999);
		}
		if(includeInTransit) {
			count -= _invProvider.countOnRoute(item);
		}
		return count;
	}
	
	
	public int getPriority() {
		return priority;
	}
	@Override
	public LogisticsModule getSubModule(int slot) {
		return null;
	}
	
	@Override
	public void tick() {}
	
	@Override
	public boolean hasGenericInterests() {
		return false;
	}
	
	@Override
	public Set<ItemIdentifier> getSpecificInterests() {
		List<ItemIdentifierStack> result = getCraftedItems();
		if(result == null) return null;
		Set<ItemIdentifier> l1 = new TreeSet<ItemIdentifier>();
		for(ItemIdentifierStack craftable:result){
			l1.add(craftable.getItem());
		}
		//for(int i=0; i<9;i++)
		//	l1.add(getMaterials(i));
		return l1;
	}
	
	@Override
	public boolean interestedInAttachedInventory() {
		return false;
		// when we are default we are interested in everything anyway, otherwise we're only interested in our filter.
	}
	
	@Override
	public boolean interestedInUndamagedID() {
		return false;
	}
	
	@Override
	public boolean recievePassive() {
		return false;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public Icon getIconTexture(IconRegister register) {
		return register.registerIcon("logisticspipes:itemModule/ModuleCrafter");
	}

	@Override
	public void canProvide(RequestTreeNode tree, int donePromisses,
			List<IFilter> filters) {
		
		if (_extras.isEmpty()) return;
		
		ItemIdentifier requestedItem = tree.getStackItem();
		List<ItemIdentifierStack> providedItem = getCraftedItems();
		for(ItemIdentifierStack item:providedItem) {
			if(item.getItem() == requestedItem) {
				return;
			}
		}
		if (!providedItem.contains(requestedItem)) return;

		
		for(IFilter filter:filters) {
			if(filter.isBlocked() == filter.isFilteredItem(requestedItem.getUndamaged()) || filter.blockProvider()) return;
		}
		int remaining = 0;
		for(LogisticsOrder extra:_extras){
			if(extra.getItem().getItem()==requestedItem){
				remaining += extra.getItem().getStackSize();
			}
				
		}
		remaining -= donePromisses;
		if (remaining < 1) return;
		LogisticsExtraPromise promise = new LogisticsExtraPromise();
		promise.item = requestedItem;
		promise.numberOfItems = Math.min(remaining, tree.getMissingItemCount());
		promise.sender = this;
		promise.provided = true;
		tree.addPromise(promise);
		
	}

	@Override
	public LogisticsOrder fullFill(LogisticsPromise promise,
			IRequestItems destination) {
		if (promise instanceof LogisticsExtraPromise) {
			removeExtras(promise.numberOfItems, promise.item);
		}
		MainProxy.sendSpawnParticlePacket(Particles.WhiteParticle, getX(), getY(), getZ(), this.getWorld(), 2);
		return _invProvider.getOrderManager().addOrder(new ItemIdentifierStack(promise.item, promise.numberOfItems), destination,RequestType.CRAFTING);
	}

	@Override
	public void getAllItems(Map<ItemIdentifier, Integer> list,
			List<IFilter> filter) {		
		
	}

	@Override
	public IRouter getRouter() {
		return _invProvider.getRouter();
	}

	@Override
	public void itemCouldNotBeSend(ItemIdentifierStack item) {
		// TODO Auto-generated method stub
		_invRequester.itemCouldNotBeSend(item);
		
	}

	@Override
	public int getID() {
		// TODO Auto-generated method stub
		return this._invProvider.getRouter().getSimpleID();
	}

	@Override
	public int compareTo(IRequestItems value2) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void registerExtras(LogisticsPromise promise) {
		ItemIdentifierStack stack = new ItemIdentifierStack(promise.item,promise.numberOfItems);
		_extras.add(new LogisticsOrder(stack, null, RequestType.EXTRA));
		LogisticsPipes.requestLog.info(stack.getStackSize() + " extras registered");
		
	}

	@Override
	public CraftingTemplate addCrafting(ItemIdentifier toCraft) {

		List<ItemIdentifierStack> stack = getCraftedItems(); 
		if (stack == null) return null;
		boolean found = false;
		ItemIdentifierStack craftingStack = null;
		for(ItemIdentifierStack craftable:stack) {
			craftingStack = craftable;
			if(craftingStack.getItem().equals(toCraft)) {
				found = true;
				break;
			}
				
		}
		if(found == false)
			return null;

		IRequestItems[] target = new IRequestItems[9];
		for(int i=0;i<9;i++) {
			target[i] = this;
		}

		boolean hasSatellite = isSatelliteConnected();
		if(!hasSatellite) return null;
		if(!getUpgradeManager().isAdvancedSatelliteCrafter()) {
			if(satelliteId != 0) {
				IRouter r = getSatelliteRouter(-1);
				if(r != null) {
					IRequestItems sat = r.getPipe();
					for(int i=6;i<9;i++) {
						target[i] = sat;
					}
				}
			}
		} else {
			for(int i=0;i<9;i++) {
				if(advancedSatelliteIdArray[i] != 0) {
					IRouter r = getSatelliteRouter(i);
					if(r != null) target[i] = r.getPipe();
				}
			}
		}

		CraftingTemplate template = new CraftingTemplate(craftingStack, this, priority);

		//Check all materials
		for (int i = 0; i < 9; i++){
			ItemIdentifierStack resourceStack = getMaterials(i);
			if (resourceStack == null || resourceStack.getStackSize() == 0) continue;
			CraftingRequirement req = new CraftingRequirement();
			req.stack = resourceStack;
			if(getUpgradeManager().isFuzzyCrafter())
			{
				if((fuzzyCraftingFlagArray[i] & 0x1) != 0)
					req.use_od = true;
				if((fuzzyCraftingFlagArray[i] & 0x2) != 0)
					req.ignore_dmg = true;
				if((fuzzyCraftingFlagArray[i] & 0x4) != 0)
					req.ignore_nbt = true;
				if((fuzzyCraftingFlagArray[i] & 0x8) != 0)
					req.use_category = true;
			}
			template.addRequirement(req, target[i]);
		}
		
		int liquidCrafter = this.getUpgradeManager().getFluidCrafter();
		IRequestFluid[] liquidTarget = new IRequestFluid[liquidCrafter];
		
		if(!getUpgradeManager().isAdvancedSatelliteCrafter()) {
			if(liquidSatelliteId != 0) {
				IRouter r = getFluidSatelliteRouter(-1);
				if(r != null) {
					IRequestFluid sat = (IRequestFluid) r.getPipe();
					for(int i=0;i<liquidCrafter;i++) {
					liquidTarget[i] = sat;
				}
			}
			}
		} else {
			for(int i=0;i<liquidCrafter;i++) {
				if(liquidSatelliteIdArray[i] != 0) {
					IRouter r = getFluidSatelliteRouter(i);
					if(r != null) liquidTarget[i] = (IRequestFluid) r.getPipe();
				}
			}
		}
		
		for (int i = 0; i < liquidCrafter; i++){
			FluidIdentifier liquid = getFluidMaterial(i);
			int amount = getFluidAmount()[i];
			if (liquid == null || amount <= 0 || liquidTarget[i] == null) continue;
			template.addRequirement(liquid, amount, liquidTarget[i]);
		}
		
		if(this.getUpgradeManager().hasByproductExtractor() && getByproductItem() != null) {
			template.addByproduct(getByproductItem());
		}
		
		return template;
	}

	private UpgradeManager getUpgradeManager() {
		return _invProvider.getUpgradeManager();
	}

	public boolean isSatelliteConnected() {
	final List<ExitRoute> routes = getRouter().getIRoutersByCost();
		if(!_invProvider.getUpgradeManager().isAdvancedSatelliteCrafter()) {
			if(satelliteId == 0) return true;
			for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
				if (satellite.satelliteId == satelliteId) {
					CoreRoutedPipe satPipe = satellite;
					if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null)
						continue;
					IRouter satRouter = satPipe.getRouter();
					for (ExitRoute route:routes) {
						if (route.destination == satRouter) {
							return true;
						}
					}
				}
			}
		} else {
			boolean foundAll = true;
			for(int i=0;i<9;i++) {
				boolean foundOne = false;
				if(advancedSatelliteIdArray[i] == 0) {
					continue;
				}
				for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
					if (satellite.satelliteId == advancedSatelliteIdArray[i]) {
						CoreRoutedPipe satPipe = satellite;
						if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null)
							continue;
						IRouter satRouter = satPipe.getRouter();
						for (ExitRoute route:routes) {
							if (route.destination == satRouter) {
								foundOne = true;
								break;
							}
						}
					}
				}
				foundAll &= foundOne;
			}
			return foundAll;
		}
		//TODO check for FluidCrafter
		return false;
	}

	@Override
	public List<ItemIdentifierStack> getCraftedItems() {
		//TODO: AECrafting check.
		List<ItemIdentifierStack> list = new ArrayList<ItemIdentifierStack>(1);
		if(_dummyInventory.getIDStackInSlot(9)!=null)
			list.add(_dummyInventory.getIDStackInSlot(9));
		return list;
	}

	@Override
	public int getTodo() {
		return  this._invProvider.getOrderManager().totalItemsCountInAllOrders();
	}


	@Override
	public int getGuiHandlerID() {
		return GuiIDs.GUI_CRAFTINGPIPE_ID;
	}



	protected int getNextConnectSatelliteId(boolean prev, int x) {
		int closestIdFound = prev ? 0 : Integer.MAX_VALUE;
		for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
			CoreRoutedPipe satPipe = satellite;
			if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null || satPipe.isFluidPipe()) continue;
			IRouter satRouter = satPipe.getRouter();
			List<ExitRoute> routes = getRouter().getDistanceTo(satRouter);
			if(routes != null && !routes.isEmpty()) {
				boolean filterFree = false;
				for(ExitRoute route: routes) {
					if(route.filters.isEmpty()) {
						filterFree = true;
						break;
					}
				}
				if(!filterFree) continue;
				if(x == -1) {
					if (!prev && satellite.satelliteId > satelliteId && satellite.satelliteId < closestIdFound) {
						closestIdFound = satellite.satelliteId;
					} else if (prev && satellite.satelliteId < satelliteId && satellite.satelliteId > closestIdFound) {
						closestIdFound = satellite.satelliteId;
					}
				} else {
					if (!prev && satellite.satelliteId > advancedSatelliteIdArray[x] && satellite.satelliteId < closestIdFound) {
						closestIdFound = satellite.satelliteId;
					} else if (prev && satellite.satelliteId < advancedSatelliteIdArray[x] && satellite.satelliteId > closestIdFound) {
						closestIdFound = satellite.satelliteId;
					}
				}
			}
		}
		if (closestIdFound == Integer.MAX_VALUE) {
			if(x == -1) {
				return satelliteId;
			} else {
				return advancedSatelliteIdArray[x];
			}
		}
		return closestIdFound;
	}
	
	protected int getNextConnectFluidSatelliteId(boolean prev, int x) {
		int closestIdFound = prev ? 0 : Integer.MAX_VALUE;
		for (final PipeFluidSatellite satellite : PipeFluidSatellite.AllSatellites) {
			CoreRoutedPipe satPipe = satellite;
			if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null || !satPipe.isFluidPipe()) continue;
			IRouter satRouter = satPipe.getRouter();
			List<ExitRoute> routes = getRouter().getDistanceTo(satRouter);
			if(routes != null && !routes.isEmpty()) {
				boolean filterFree = false;
				for(ExitRoute route: routes) {
					if(route.filters.isEmpty()) {
						filterFree = true;
						break;
					}
				}
				if(!filterFree) continue;
				if(x == -1) {
					if (!prev && satellite.satelliteId > liquidSatelliteId && satellite.satelliteId < closestIdFound) {
						closestIdFound = satellite.satelliteId;
					} else if (prev && satellite.satelliteId < liquidSatelliteId && satellite.satelliteId > closestIdFound) {
						closestIdFound = satellite.satelliteId;
					}
				} else {
					if (!prev && satellite.satelliteId > liquidSatelliteIdArray[x] && satellite.satelliteId < closestIdFound) {
						closestIdFound = satellite.satelliteId;
					} else if (prev && satellite.satelliteId < liquidSatelliteIdArray[x] && satellite.satelliteId > closestIdFound) {
						closestIdFound = satellite.satelliteId;
					}
				}
			}
		}
		if (closestIdFound == Integer.MAX_VALUE) {
			if(x == -1) {
				return liquidSatelliteId;
			} else {
				return liquidSatelliteIdArray[x];
			}
		}
		return closestIdFound;
	}

	public void setNextSatellite(EntityPlayer player) {
		if (MainProxy.isClient(player.worldObj)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(CPipeNextSatellite.class).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToServer(packet);
		} else {
			satelliteId = getNextConnectSatelliteId(false, -1);
			final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteId.class).setPipeId(satelliteId).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToPlayer(packet, (Player)player);
		}

	}
	
	// This is called by the packet PacketCraftingPipeSatelliteId
	public void setSatelliteId(int satelliteId, int x) {
		if(x == -1) {
			this.satelliteId = satelliteId;
		} else {
			advancedSatelliteIdArray[x] = satelliteId;
		}
	}

	public void setPrevSatellite(EntityPlayer player) {
		if (MainProxy.isClient(player.worldObj)) {
			final CoordinatesPacket packet = PacketHandler.getPacket(CPipePrevSatellite.class).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToServer(packet);
		} else {
			satelliteId = getNextConnectSatelliteId(true, -1);
			final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteId.class).setPipeId(satelliteId).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToPlayer(packet, (Player)player);
		}
	}

	public IRouter getSatelliteRouter(int x) {
		if(x == -1) {
			for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
				if (satellite.satelliteId == satelliteId) {
					CoreRoutedPipe satPipe = satellite;
					if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null)
						continue;
					return satPipe.getRouter();
				}
			}
		} else {
			for (final PipeItemsSatelliteLogistics satellite : PipeItemsSatelliteLogistics.AllSatellites) {
				if (satellite.satelliteId == advancedSatelliteIdArray[x]) {
					CoreRoutedPipe satPipe = satellite;
					if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null)
						continue;
					return satPipe.getRouter();
				}
			}
		}
		return null;
	}
	
	
	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound) {
//		super.readFromNBT(nbttagcompound);
		_dummyInventory.readFromNBT(nbttagcompound, "");
		_liquidInventory.readFromNBT(nbttagcompound, "FluidInv");
		satelliteId = nbttagcompound.getInteger("satelliteid");
		
		priority = nbttagcompound.getInteger("priority");
		for(int i=0;i<9;i++) {
			advancedSatelliteIdArray[i] = nbttagcompound.getInteger("advancedSatelliteId" + i);
		}
		for(int i=0;i<9;i++) {
			fuzzyCraftingFlagArray[i] = nbttagcompound.getByte("fuzzyCraftingFlag" + i);
		}
		for(int i=0;i<6;i++) {
			craftingSigns[i] = nbttagcompound.getBoolean("craftingSigns" + i);
		}
		if(nbttagcompound.hasKey("FluidAmount")) {
			amount = nbttagcompound.getIntArray("FluidAmount");
		}
		if(amount.length < ItemUpgrade.MAX_LIQUID_CRAFTER) {
			amount = new int[ItemUpgrade.MAX_LIQUID_CRAFTER];
		}
		for(int i=0;i<ItemUpgrade.MAX_LIQUID_CRAFTER;i++) {
			liquidSatelliteIdArray[i] = nbttagcompound.getInteger("liquidSatelliteIdArray" + i);
		}
		for(int i=0;i<ItemUpgrade.MAX_LIQUID_CRAFTER;i++) {
			liquidSatelliteIdArray[i] = nbttagcompound.getInteger("liquidSatelliteIdArray" + i);
		}
		liquidSatelliteId = nbttagcompound.getInteger("liquidSatelliteId"); 
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
	//	super.writeToNBT(nbttagcompound);
		_dummyInventory.writeToNBT(nbttagcompound, "");
		_liquidInventory.writeToNBT(nbttagcompound, "FluidInv");
		nbttagcompound.setInteger("satelliteid", satelliteId);
		
		nbttagcompound.setInteger("priority", priority);
		for(int i=0;i<9;i++) {
			nbttagcompound.setInteger("advancedSatelliteId" + i, advancedSatelliteIdArray[i]);
		}
		for(int i=0;i<9;i++) {
			nbttagcompound.setByte("fuzzyCraftingFlag" + i, (byte)fuzzyCraftingFlagArray[i]);
		}
		for(int i=0;i<6;i++) {
			nbttagcompound.setBoolean("craftingSigns" + i, craftingSigns[i]);
		}
		for(int i=0;i<ItemUpgrade.MAX_LIQUID_CRAFTER;i++) {
			nbttagcompound.setInteger("liquidSatelliteIdArray" + i, liquidSatelliteIdArray[i]);
		}
		nbttagcompound.setIntArray("FluidAmount", amount);
		nbttagcompound.setInteger("liquidSatelliteId", liquidSatelliteId);
	}
	
	public ModernPacket getCPipePacket() {
		return PacketHandler.getPacket(CraftingPipeUpdatePacket.class).setAmount(amount).setLiquidSatelliteIdArray(liquidSatelliteIdArray).setLiquidSatelliteId(liquidSatelliteId).setSatelliteId(satelliteId).setAdvancedSatelliteIdArray(advancedSatelliteIdArray).setFuzzyCraftingFlagArray(fuzzyCraftingFlagArray).setPriority(priority).setPosX(getX()).setPosY(getY()).setPosZ(getZ());	}
	
	public void handleCraftingUpdatePacket(CraftingPipeUpdatePacket packet) {
		amount = packet.getAmount();
		liquidSatelliteIdArray = packet.getLiquidSatelliteIdArray();
		liquidSatelliteId = packet.getLiquidSatelliteId();
		satelliteId = packet.getSatelliteId();
		advancedSatelliteIdArray = packet.getAdvancedSatelliteIdArray();
		fuzzyCraftingFlagArray = packet.getFuzzyCraftingFlagArray();
		priority = packet.getPriority();
	}
	
	@Override
	public void sendGuiArgs(EntityPlayer entityplayer) {
		MainProxy.sendPacketToPlayer(PacketHandler.getPacket(GuiArgument.class)
				.setGuiID(getGuiHandlerID())
				.setArgs(new Object[]{getUpgradeManager().isAdvancedSatelliteCrafter(),
						getUpgradeManager().getFluidCrafter(),
						amount,
						getUpgradeManager().hasByproductExtractor(),
						getUpgradeManager().isFuzzyCrafter()}),
						(Player) entityplayer);
		//entityplayer.openGui(LogisticsPipes.instance, getGuiHandlerID(), getWorld(), getX(), getY(), getZ());
	}
	
	public List<ForgeDirection> getCraftingSigns() {
		List<ForgeDirection> list = new ArrayList<ForgeDirection>();
		for(int i=0;i<6;i++) {
			if(craftingSigns[i]) {
				list.add(ForgeDirection.VALID_DIRECTIONS[i]);
			}
		}
		return list;
	}

	public boolean setCraftingSign(ForgeDirection dir, boolean b, EntityPlayer player) {
		if(dir.ordinal() < 6) {
			if(craftingSigns[dir.ordinal()] != b) {
				craftingSigns[dir.ordinal()] = b;
				final ModernPacket packetA = this.getCPipePacket();
				final ModernPacket packetB = PacketHandler.getPacket(CPipeSatelliteImportBack.class).setInventory(getDummyInventory()).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
				if(player != null) {
					MainProxy.sendPacketToPlayer(packetA, (Player)player);
					MainProxy.sendPacketToPlayer(packetB, (Player)player);
				}
				MainProxy.sendPacketToAllWatchingChunk(getX(), getZ(), MainProxy.getDimensionForWorld(_invProvider.getWorld()), packetA);
				MainProxy.sendPacketToAllWatchingChunk(getX(), getZ(), MainProxy.getDimensionForWorld(_invProvider.getWorld()), packetB);
				_pipe.refreshRender(false);
				return true;
			}
		}
		return false;
	}

	/**
	 * Simply get the dummy inventory
	 * 
	 * @return the dummy inventory
	 */
	public ItemIdentifierInventory getDummyInventory() {
		return _dummyInventory;
	}

	public ItemIdentifierInventory getFluidInventory() {
		return _liquidInventory;
	}
	
	public void setDummyInventorySlot(int slot, ItemStack itemstack) {
		_dummyInventory.setInventorySlotContents(slot, itemstack);
	}
	
		public void importFromCraftingTable(EntityPlayer player) {
		if (MainProxy.isClient(getWorld())) {
			// Send packet asking for import
			final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteImport.class).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			MainProxy.sendPacketToServer(packet);
		} else{
			boolean fuzzyFlagsChanged = false;
			final WorldUtil worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
			for (final AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
				for (ICraftingRecipeProvider provider : SimpleServiceLocator.craftingRecipeProviders) {
					if (provider.importRecipe(tile.tile, _dummyInventory)) {
						if (provider instanceof IFuzzyRecipeProvider) {
							fuzzyFlagsChanged = ((IFuzzyRecipeProvider)provider).importFuzzyFlags(tile.tile, _dummyInventory, fuzzyCraftingFlagArray);
						}
						break;
					}
				}
			}
			// Send inventory as packet
			final CoordinatesPacket packet = PacketHandler.getPacket(CPipeSatelliteImportBack.class).setInventory(_dummyInventory).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			if(player != null) {
				MainProxy.sendPacketToPlayer(packet, (Player)player);
			}
			MainProxy.sendPacketToAllWatchingChunk(this.getX(), this.getZ(), MainProxy.getDimensionForWorld(getWorld()), packet);
			
			if(fuzzyFlagsChanged && this.getUpgradeManager().isFuzzyCrafter()) {
				for (int i = 0; i < 9; i++) {
					final ModernPacket pak = PacketHandler.getPacket(CraftingFuzzyFlag.class).setInteger2(fuzzyCraftingFlagArray[i]).setInteger(i).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
					if(player != null)
						MainProxy.sendPacketToPlayer(pak, (Player)player);
					MainProxy.sendPacketToAllWatchingChunk(this.getX(), this.getZ(), MainProxy.getDimensionForWorld(getWorld()), pak);
				}
			}
		}
	}

	private World getWorld() {
			return _invProvider.getWorld();
		}

	public void handleStackMove(int number) {
		if(MainProxy.isClient(this.getWorld())) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingPipeStackMovePacket.class).setInteger(number).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		}
		ItemStack stack = _dummyInventory.getStackInSlot(number);
		if(stack == null ) return;
		for(int i = 6;i < 9;i++) {
			ItemStack stackb = _dummyInventory.getStackInSlot(i);
			if(stackb == null) {
				_dummyInventory.setInventorySlotContents(i, stack);
				_dummyInventory.clearInventorySlotContents(number);
				break;
			}
		}
	}
	
	public void priorityUp(EntityPlayer player) {
		priority++;
		if(MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingPipePriorityUpPacket.class).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else if(player != null && MainProxy.isServer(player.worldObj)) {
			MainProxy.sendPacketToPlayer(PacketHandler.getPacket(CraftingPriority.class).setInteger(priority).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
		}
	}
	
	public void priorityDown(EntityPlayer player) {
		priority--;
		if(MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingPipePriorityDownPacket.class).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else if(player != null && MainProxy.isServer(player.worldObj)) {
			MainProxy.sendPacketToPlayer(PacketHandler.getPacket(CraftingPriority.class).setInteger(priority).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
		}
	}
	
	public void setPriority(int amount) {
		priority = amount;
	}

	public ItemIdentifierStack getByproductItem() {
		return _dummyInventory.getIDStackInSlot(10);
	}
	
	public ItemIdentifierStack getMaterials(int slotnr) {
		return _dummyInventory.getIDStackInSlot(slotnr);
	}

	public FluidIdentifier getFluidMaterial(int slotnr) {
		ItemIdentifierStack stack = _liquidInventory.getIDStackInSlot(slotnr);
		if(stack == null) return null;
		return FluidIdentifier.get(stack.getItem());
	}
	

	public void setNextSatellite(EntityPlayer player, int i) {
		if (MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingPipeNextAdvancedSatellitePacket.class).setInteger(i).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else {
			advancedSatelliteIdArray[i] = getNextConnectSatelliteId(false, i);
			MainProxy.sendPacketToPlayer(PacketHandler.getPacket(CraftingAdvancedSatelliteId.class).setInteger2(i).setInteger(advancedSatelliteIdArray[i]).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
		}
	}

	public void setPrevSatellite(EntityPlayer player, int i) {
		if (MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingPipePrevAdvancedSatellitePacket.class).setInteger(i).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else {
			advancedSatelliteIdArray[i] = getNextConnectSatelliteId(true, i);
			MainProxy.sendPacketToPlayer(PacketHandler.getPacket(CraftingAdvancedSatelliteId.class).setInteger2(i).setInteger(advancedSatelliteIdArray[i]).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
		}
	}

	public void changeFluidAmount(int change, int slot, EntityPlayer player) {
		if (MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(FluidCraftingAmount.class).setInteger2(slot).setInteger(change).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else {
			amount[slot] += change;
			if(amount[slot] <= 0) {
				amount[slot] = 0;
			}
			MainProxy.sendPacketToPlayer(PacketHandler.getPacket(FluidCraftingAmount.class).setInteger2(slot).setInteger(amount[slot]).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
		}
	}

		public void setPrevFluidSatellite(EntityPlayer player, int i) {
		if (MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(FluidCraftingPipeAdvancedSatellitePrevPacket.class).setInteger(i).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else {
			if(i == -1) {
				liquidSatelliteId = getNextConnectFluidSatelliteId(true, i);
				MainProxy.sendPacketToPlayer(PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i).setInteger(liquidSatelliteId).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
			} else {
				liquidSatelliteIdArray[i] = getNextConnectFluidSatelliteId(true, i);
				MainProxy.sendPacketToPlayer(PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i).setInteger(liquidSatelliteIdArray[i]).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
			}
		}
	}

	public void setNextFluidSatellite(EntityPlayer player, int i) {
		if (MainProxy.isClient(player.worldObj)) {
			MainProxy.sendPacketToServer(PacketHandler.getPacket(FluidCraftingPipeAdvancedSatelliteNextPacket.class).setInteger(i).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
		} else {
			if(i == -1) {
				liquidSatelliteId = getNextConnectFluidSatelliteId(false, i);
				MainProxy.sendPacketToPlayer(PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i).setInteger(liquidSatelliteId).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
			} else {
				liquidSatelliteIdArray[i] = getNextConnectFluidSatelliteId(false, i);
				MainProxy.sendPacketToPlayer(PacketHandler.getPacket(FluidCraftingAdvancedSatelliteId.class).setInteger2(i).setInteger(liquidSatelliteIdArray[i]).setPosX(getX()).setPosY(getY()).setPosZ(getZ()), (Player)player);
			}
		}
	}

	public void setFluidAmount(int[] amount) {
		if(MainProxy.isClient(getWorld())) {
			this.amount = amount;
		}
	}

	public void defineFluidAmount(int integer, int slot) {
		if(MainProxy.isClient(getWorld())) {
			amount[slot] = integer;
		}
	}
	
	public int[] getFluidAmount() {
		return amount;
	}

	public void setFluidSatelliteId(int integer, int slot) {
		if(slot == -1) {
			liquidSatelliteId = integer;
		} else {
			liquidSatelliteIdArray[slot] = integer;
		}	
	}


	public IRouter getFluidSatelliteRouter(int x) {
		if(x == -1) {
			for (final PipeFluidSatellite satellite : PipeFluidSatellite.AllSatellites) {
				if (satellite.satelliteId == liquidSatelliteId) {
					CoreRoutedPipe satPipe = satellite;
					if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null)
						continue;
					return satPipe.getRouter();
				}
			}
		} else {
			for (final PipeFluidSatellite satellite : PipeFluidSatellite.AllSatellites) {
				if (satellite.satelliteId == liquidSatelliteIdArray[x]) {
					CoreRoutedPipe satPipe = satellite;
					if(satPipe == null || satPipe.stillNeedReplace() || satPipe.getRouter() == null)
						continue;
					return satPipe.getRouter();
				}
			}
		}
		return null;
	}

	public void setFuzzyCraftingFlag(int slot, int flag, EntityPlayer player)
	{
		if(slot < 0 || slot >= 9)
			return;
		if(MainProxy.isClient(this.getWorld()))
			if(player == null)
				MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingFuzzyFlag.class).setInteger2(flag).setInteger(slot).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
			else
				fuzzyCraftingFlagArray[slot] = flag;
		else
		{
			fuzzyCraftingFlagArray[slot] ^= 1 << flag;
			ModernPacket pak = PacketHandler.getPacket(CraftingFuzzyFlag.class).setInteger2(fuzzyCraftingFlagArray[slot]).setInteger(slot).setPosX(getX()).setPosY(getY()).setPosZ(getZ());
			if(player != null)
				MainProxy.sendPacketToPlayer(pak, (Player)player);
			MainProxy.sendPacketToAllWatchingChunk(getX(), getZ(), MainProxy.getDimensionForWorld(getWorld()), pak);
		}
	}
/*
	public boolean hasCraftingSign() {
		for(int i=0;i<6;i++) {
			if(signItem[i] instanceof CraftingPipeSign) {
				return true;
			}
		}
		return false;
	}*/
/*
	@Override
	public void startWatching() {
		MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStartWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
	}

	@Override
	public void stopWatching() {
		MainProxy.sendPacketToServer(PacketHandler.getPacket(HUDStopWatchingPacket.class).setInteger(1).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
	}
*/


	public void openAttachedGui(EntityPlayer player) {
			 		if (MainProxy.isClient(player.worldObj)) {
			 			if(player instanceof EntityPlayerMP) {
			 				((EntityPlayerMP)player).closeScreen();
			 			} else if(player instanceof EntityPlayerSP) {
			 				((EntityPlayerSP)player).closeScreen();
			 			}
			 			MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingPipeOpenConnectedGuiPacket.class).setPosX(getX()).setPosY(getY()).setPosZ(getZ()));
			 			return;
			 		}
			 
			 		//hack to avoid wrenching blocks
			 		int savedEquipped = player.inventory.currentItem;
			 		boolean foundSlot = false;
			 		//try to find a empty slot
			 		for(int i = 0; i < 9; i++) {
			 			if(player.inventory.getStackInSlot(i) == null) {
			 				foundSlot = true;
			 				player.inventory.currentItem = i;
			 				break;
			 			}
			 		}
			 		//okay, anything that's a block?
			 		if(!foundSlot) {
			 			for(int i = 0; i < 9; i++) {
			 				ItemStack is = player.inventory.getStackInSlot(i);
			 				if(is.getItem() instanceof ItemBlock) {
			 					foundSlot = true;
			 					player.inventory.currentItem = i;
			 					break;
			 				}
			 			}
			 		}
			 		//give up and select whatever is right of the current slot
			 		if(!foundSlot) {
			 			player.inventory.currentItem = (player.inventory.currentItem + 1) % 9;
			 		}
			 
			 		final WorldUtil worldUtil = new WorldUtil(getWorld(), getX(), getY(), getZ());
			 		boolean found = false;
			 		for (final AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)) {
			 			for (ICraftingRecipeProvider provider : SimpleServiceLocator.craftingRecipeProviders) {
			 				if (provider.canOpenGui(tile.tile)) {
			 					found = true;
			 					break;
			 				}
			 			}
			 
			 			if (!found)
			 				found = (tile.tile instanceof IInventory && !(tile.tile instanceof TileGenericPipe));
			 
			 			if (found) {
			 				Block block = getWorld().getBlockId(tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord) < Block.blocksList.length ? Block.blocksList[getWorld().getBlockId(tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord)] : null;
			 				if(block != null) {
			 					if(block.onBlockActivated(getWorld(), tile.tile.xCoord, tile.tile.yCoord, tile.tile.zCoord, player, 0, 0, 0, 0)){
			 						break;
			 					}
			 				}
			 			}
			 		}
			 
			 		player.inventory.currentItem = savedEquipped;
		
	}


	public void enabledUpdateEntity() {
		if(_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING)) {
			cacheAreAllOrderesToBuffer();
			if(_invProvider.getOrderManager().isFirstOrderWatched()) {
				TileEntity tile = lastAccessedCrafter.get();
				if(tile != null) {
					_invProvider.getOrderManager().setMachineProgress(SimpleServiceLocator.machineProgressProvider.getProgressForTile(tile));
				} else {
					_invProvider.getOrderManager().setMachineProgress((byte) 0);
				}
			}
		} else {
			cachedAreAllOrderesToBuffer = false;
		}
		
		if (getWorld().getTotalWorldTime() % 6 != 0) return;

		waitingForCraft = false;
		
		if((!_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING) && _extras.isEmpty())) return;
		
		waitingForCraft = true;
		
		List<AdjacentTile> crafters = locateCrafters();
		if (crafters.size() < 1 ) {
			if (_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING)) {
				_invProvider.getOrderManager().sendFailed();
			} else {
				_extras.clear();
			}
			return;
		}
		
		List<ItemIdentifierStack> wanteditem = getCraftedItems();
		if(wanteditem == null || wanteditem.isEmpty()) return;

		MainProxy.sendSpawnParticlePacket(Particles.VioletParticle, getX(), getY(), getZ(), this.getWorld(), 2);
		
		int itemsleft = itemsToExtract();
		int stacksleft = stacksToExtract();
		while (itemsleft > 0 && stacksleft > 0 && (_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING) || !_extras.isEmpty())) {
			LogisticsOrder nextOrder;
			boolean processingOrder=false;
			if(_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING)){
				nextOrder = _invProvider.getOrderManager().peekAtTopRequest(RequestType.CRAFTING); // fetch but not remove.
				processingOrder=true;
			} else {
				nextOrder = _extras.getFirst(); // fetch but not remove.
			}
			int maxtosend = Math.min(itemsleft, nextOrder.getItem().getStackSize());
			maxtosend = Math.min(nextOrder.getItem().getItem().getMaxStackSize(), maxtosend);
			// retrieve the new crafted items
			ItemStack extracted = null;
			AdjacentTile tile = null;
			for (Iterator<AdjacentTile> it = crafters.iterator(); it.hasNext();) {
				tile = it.next();
				if (tile.tile instanceof LogisticsCraftingTableTileEntity) {
					extracted = extractFromLogisticsCraftingTable((LogisticsCraftingTableTileEntity)tile.tile, nextOrder.getItem().getItem(), maxtosend);
				} else if (tile.tile instanceof ISpecialInventory) {
					extracted = extractFromISpecialInventory((ISpecialInventory) tile.tile, nextOrder.getItem().getItem(), maxtosend);
				} else if (tile.tile instanceof net.minecraft.inventory.ISidedInventory) {
					IInventory sidedadapter = new SidedInventoryMinecraftAdapter((net.minecraft.inventory.ISidedInventory) tile.tile, ForgeDirection.UNKNOWN,true);
					extracted = extractFromIInventory(sidedadapter, nextOrder.getItem().getItem(), maxtosend);
				} else if (tile.tile instanceof IInventory) {
					extracted = extractFromIInventory((IInventory)tile.tile, nextOrder.getItem().getItem(), maxtosend);
				}
				if (extracted != null && extracted.stackSize > 0) {
					break;
				}
			}
			if(extracted == null || extracted.stackSize == 0) break;
			lastAccessedCrafter = new WeakReference<TileEntity>(tile.tile);
			// send the new crafted items to the destination
			ItemIdentifier extractedID = ItemIdentifier.get(extracted);
			while (extracted.stackSize > 0) {
				if(nextOrder.getItem().getItem() != extractedID) {
					LogisticsOrder startOrder = nextOrder;
					if(_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING)) {
					do {
						_invProvider.getOrderManager().deferSend();
						nextOrder = _invProvider.getOrderManager().peekAtTopRequest(RequestType.CRAFTING);
					} while(nextOrder.getItem().getItem() != extractedID && startOrder != nextOrder);
					}
					if(startOrder == nextOrder) {
						int numtosend = Math.min(extracted.stackSize, extractedID.getMaxStackSize());
						if(numtosend == 0)
							break;
						stacksleft -= 1;
						itemsleft -= numtosend;
						ItemStack stackToSend = extracted.splitStack(numtosend);
						//Route the unhandled item
						
						_invProvider.sendStack(stackToSend, nextOrder.getRouterId(),ItemSendMode.Normal) ;
						continue;
					}
				}
				int numtosend = Math.min(extracted.stackSize, extractedID.getMaxStackSize());
				numtosend = Math.min(numtosend, nextOrder.getItem().getStackSize()); 
				if(numtosend == 0)
					break;
				stacksleft -= 1;
				itemsleft -= numtosend;
				ItemStack stackToSend = extracted.splitStack(numtosend);
				if (processingOrder) {
					SinkReply reply = LogisticsManager.canSink(nextOrder.getDestination().getRouter(), null, true, ItemIdentifier.get(stackToSend), null, true, false);
					boolean defersend = false;
					if(reply == null || reply.bufferMode != BufferMode.NONE || reply.maxNumberOfItems < 1) {
						defersend = true;
					}
					IRoutedItem item = SimpleServiceLocator.routedItemHelper.createNewTravelItem(stackToSend);
					item.setDestination(nextOrder.getDestination().getRouter().getSimpleID());
					item.setTransportMode(TransportMode.Active);
					_invProvider.queueRoutedItem(item, tile.orientation);
					_invProvider.getOrderManager().sendSuccessfull(stackToSend.stackSize, defersend, item);
					if(_invProvider.getOrderManager().hasOrders(RequestType.CRAFTING)){
						nextOrder = _invProvider.getOrderManager().peekAtTopRequest(RequestType.CRAFTING); // fetch but not remove.
					} else {
						processingOrder = false;
						if(!_extras.isEmpty())
						nextOrder = _extras.getFirst();
					}
				} else {
					removeExtras(numtosend,nextOrder.getItem().getItem());
					_invProvider.sendStack(stackToSend, nextOrder.getRouterId(),ItemSendMode.Normal) ;
				}
			}
		}
		
	}
	private boolean cachedAreAllOrderesToBuffer;
	
	public boolean areAllOrderesToBuffer() {
		return cachedAreAllOrderesToBuffer;
	}
	
	public void cacheAreAllOrderesToBuffer() {
		boolean result = true;
		for(LogisticsOrder order:_invProvider.getOrderManager()) {
			SinkReply reply = LogisticsManager.canSink(order.getDestination().getRouter(), null, true, order.getItem().getItem(), null, true, false);
			if(reply != null && reply.bufferMode != BufferMode.BUFFERED && reply.maxNumberOfItems >= 1) {
				result = false;
			}
		}
		cachedAreAllOrderesToBuffer = result;
	}
	
	private void removeExtras(int numToSend, ItemIdentifier item) {
		Iterator<LogisticsOrder> i = _extras.iterator();
		while(i.hasNext()){
			ItemIdentifierStack e = i.next().getItem();
			if(e.getItem()== item) {
				if(numToSend >= e.getStackSize()) {
					numToSend -= e.getStackSize();
					i.remove();
					if(numToSend == 0) {
						return;
					}
				} else {
					e.setStackSize(e.getStackSize() - numToSend);
					break;
				}
			}
		}
	}
	private ItemStack extractFromISpecialInventory(ISpecialInventory inv, ItemIdentifier wanteditem, int count){
		ItemStack retstack = null;
		while(count > 0) {
			ItemStack[] stacks = inv.extractItem(false, ForgeDirection.UNKNOWN, 1);
			if(stacks == null || stacks.length < 1 || stacks[0] == null) break;
			ItemStack stack = stacks[0];
			if(stack.stackSize == 0) break;
			if(retstack == null) {
				if(!wanteditem.fuzzyMatch(stack)) break;
			} else {
				if(!retstack.isItemEqual(stack)) break;
				if(!ItemStack.areItemStackTagsEqual(retstack, stack)) break;
			}
			if(!_power.useEnergy(neededEnergy() * stack.stackSize)) break;
			
			stacks = inv.extractItem(true, ForgeDirection.UNKNOWN, 1);
			if(stacks == null || stacks.length < 1 || stacks[0] == null) {
				LogisticsPipes.requestLog.info("crafting extractItem(true) got nothing from " + ((Object)inv).toString());
				break;
			}
			if(!ItemStack.areItemStacksEqual(stack, stacks[0])) {
				LogisticsPipes.requestLog.info("crafting extract got a unexpected item from " + ((Object)inv).toString());
			}
			if(retstack == null) {
				retstack = stack;
			} else {
				retstack.stackSize += stack.stackSize;
			}
			count -= stack.stackSize;
		}
		return retstack;
	}
	
	private ItemStack extractFromIInventory(IInventory inv, ItemIdentifier wanteditem, int count){
		IInventoryUtil invUtil = SimpleServiceLocator.inventoryUtilFactory.getInventoryUtil(inv);
		int available = invUtil.itemCount(wanteditem);
		if(available == 0) return null;
		if(!_power.useEnergy(neededEnergy() * Math.min(count, available))) {
			return null;
		}
		return invUtil.getMultipleItems(wanteditem, Math.min(count, available));
	}
	
	private ItemStack extractFromLogisticsCraftingTable(LogisticsCraftingTableTileEntity tile, ItemIdentifier wanteditem, int count) {
		ItemStack extracted = extractFromIInventory(tile, wanteditem, count);
		if(extracted != null) {
			return extracted;
		}
		ItemStack retstack = null;
		while(count > 0) {
			ItemStack stack = tile.getOutput(wanteditem, _power);
			if(stack == null || stack.stackSize == 0) break;
			if(retstack == null) {
				if(!wanteditem.fuzzyMatch(stack)) break;
			} else {
				if(!retstack.isItemEqual(stack)) break;
				if(!ItemStack.areItemStackTagsEqual(retstack, stack)) break;
			}
			if(!_power.useEnergy(neededEnergy() * stack.stackSize)) break;
			
			if(retstack == null) {
				retstack = stack;
			} else {
				retstack.stackSize += stack.stackSize;
			}
			count -= stack.stackSize;
		}
		return retstack;		
	}
	
	protected int neededEnergy() {
		return 10;
	}
	
	protected int itemsToExtract() {
		return 1;
	}
	
	protected int stacksToExtract() {
		return 1;
	}
	
	private List<AdjacentTile> _cachedCrafters = null;
	public List<AdjacentTile> locateCrafters()	{
		if(_cachedCrafters !=null)
			return _cachedCrafters;
		WorldUtil worldUtil = new WorldUtil(this.getWorld(), this.getX(), this.getY(), this.getZ());
		LinkedList<AdjacentTile> crafters = new LinkedList<AdjacentTile>();
		for (AdjacentTile tile : worldUtil.getAdjacentTileEntities(true)){
			if (tile.tile instanceof TileGenericPipe) continue;
			if (!(tile.tile instanceof IInventory)) continue;
			crafters.add(tile);
		}
		_cachedCrafters=crafters;
		return _cachedCrafters;
	}
	
	public void clearCraftersCache() {
		_cachedCrafters = null;
	}
	
	@Override
	public void clearCache() {
		clearCraftersCache();
	}
	
	

}
