package Server;

import Server.Crypto.Crypto;
import Server.Furniture.Item;
import Server.Room.Room;
import Requests.Handler;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/*
 *****************
 * @author capos *
 *****************
*/

public class Connection extends SimpleChannelUpstreamHandler
{
    public Crypto Crypto;
    public Player Data;
    public Server Environment;
    public Channel Socket;
    public DataInputStream in;
    
    public ServerMessage ClientMessage = new ServerMessage();
    
    public Connection(Server Env)
    {
        Environment = Env;
    }
    
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        Socket = e.getChannel();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        System.out.println("Unexpected exception from downstream."+e.getCause());
        Socket.disconnect();
    }
    
    
    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        if(Data != null)
        {
            Data.SetFlag(Server.plrOnline,false);

            Environment.ClientManager.SetOnline(false);

            if(Data.Id != 0)
            {
                if (Data.CurrentRoom > 0)
                {
                    Room Room = Environment.RoomManager.GetRoom(Data.CurrentRoom);

                    if (Room != null)
                    {
                        Room.RemoveUserFromRoom(this, false, false, false);
                        Data.CurrentRoom = 0;
                    }
                }

                for(int FriendId : Data.Friends)
                {
                    Player pClient = Environment.ClientManager.GetClient(FriendId);
                    if(pClient == null) continue;

                    if((pClient.Flags & Server.plrOnline) == Server.plrOnline) // Is Online?
                    {
                        pClient.FriendsUpdateNeeded.add(Data.Id);
                    }
                }

                // Unload free rooms..
                for(int RoomId : Data.OwnRooms)
                {
                    try
                    {
                        Room Room = Environment.RoomManager.GetRoom(RoomId);
                        if (Room != null)
                        {
                            if(Room.UsersNow == 0)
                            {
                                Room.StopRunning();
                                Environment.RoomManager.UnloadRoom(Room.Id);
                            }
                        }
                    }
                    catch(Exception ex)
                    {
                        Environment.Log.Print(ex);
                    }
                }


                DatabaseClient DB;
                try
                {
                    DB = new DatabaseClient(Environment.DataBase);
                }
                catch (Exception ex)
                {
                    Environment.Log.Print(ex);
                    return;
                }

                String FriendsUsersId = "";

                for(int UserId : Data.Friends)
                {
                    String sUserId = Integer.toString(UserId);
                    FriendsUsersId += Environment.b64Encode(sUserId.length()) + sUserId;
                }

                String FriendsCategories = "";

                for(String Category : Data.FriendCategories)
                {
                    FriendsCategories += Environment.b64Encode(Category.length()) + Category;
                }

                String FriendsRequests = "";

                for(int UserId : Data.Friend_Requests)
                {
                    String sUserId = Integer.toString(UserId);
                    FriendsRequests += Environment.b64Encode(sUserId.length()) + sUserId;
                }

                String MyRooms = "";

                for(int RoomId : Data.OwnRooms)
                {
                    String sRoomId = Integer.toString(RoomId);
                    MyRooms += Environment.b64Encode(sRoomId.length()) + sRoomId;
                }

                String FavoriteRooms = "";

                for(int RoomId : Data.Favorite_Rooms)
                {
                    String sRoomId = Integer.toString(RoomId);
                    FavoriteRooms += Environment.b64Encode(sRoomId.length()) + sRoomId;
                }

                String Badges = "";

                for(Badge badge : Data.Badges)
                {

                    Badges += Environment.b64Encode(badge.Code.length()) + badge.Code + Environment.b64Encode(badge.Slot);
                }


                try
                {

                    DB.SecureExec("UPDATE `users` SET "
                            + "`online` =  0 , "
                            + "`look` =  ? , "
                            + "`sex` =  '"+Data.Sex+"' , "
                            + "`mission` =  ? , "
                            + "`credits` =  '"+Data.Credits+"' ,  "
                            + "`pixels` =  '"+Data.Pixels+"' , "
                            + "`shells` =  '"+Data.Shells+"' , "
                            + "`respects` =  '"+Data.Respects+"' , "
                            + "`subscription_type` =  '"+Data.Subscription.Type+"' , "
                            + "`subscription_timestamp` =  '"+Data.Subscription.TimeExpire+"' , "
                            + "`have_normal_respects` =  '"+Data.DailyRespectPoints+"' , "
                            + "`have_pet_respects` =  '"+Data.DailyPetRespectPoints+"' , "
                            + "`friends` =  '"+FriendsUsersId+"', "
                            + "`friendcategories` =  '"+FriendsCategories+"', "
                            + "`friendrequests` =  '"+FriendsRequests+"', "
                            + "`home_room` =  '"+Data.HomeRoom+"', "
                            + "`ownrooms` =  '"+MyRooms+"', "
                            + "`salas_favoritas` =  '"+FavoriteRooms+"', "
                            + "`badges` =  '"+Badges+"', "
                            + "`totallength_hc` =  '"+Data.TotalLengthHC+"', "
                            + "`totallength_vip` =  '"+Data.TotalLengthVIP+"', "
                            + "`access_count` =  '"+Data.AccessCount+"', "
                            + "`lastvisit` =  '"+Data.LastVisit+"' "
                            + "WHERE `id` = '"+Data.Id+"' LIMIT 1;",
                            Data.Look,
                            Data.Motto);

                    for(UserItem Item : Data.InventoryItems.values())
                    {
                        if(!Item.DBNeedUpdate) continue;

                        DB.SecureExec("UPDATE items SET `room_id`=0,`extra_data`=? WHERE id = "+Item.Id+";",Item.ExtraData);
                    }

                    for(UserItem Item : Data.InventoryItemsWall.values())
                    {
                        if(!Item.DBNeedUpdate) continue;

                        DB.SecureExec("UPDATE items SET `room_id`=0,`extra_data`=? WHERE id = "+Item.Id+";",Item.ExtraData);
                    }

                }
                catch (Exception ex)
                {
                    Environment.Log.Print(ex);
                }
                DB.Close();
            }
        }
        
        try
        {
            finalize();
        }
        catch (Throwable ex)
        {
            Environment.Log.Print(ex);
        }
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    {
        try
        {
            ChannelBuffer bufferin = (ChannelBuffer) e.getMessage();
            
            System.out.println("messageReceived");
            
            if (bufferin.readableBytes() < 6) // 4 bytes of int32 len + 2 bytes of short header
            {
                Socket.disconnect();
                return;
            }
            
            if(bufferin.getByte(0) == 60)
            {
                if(Crypto == null || Crypto.RC4Decode == null)
                {
                    System.out.println("Policy");

                    byte Bytes[] = "<?xml version=\"1.0\"?>\r\n<!DOCTYPE cross-domain-policy SYSTEM \"/xml/dtds/cross-domain-policy.dtd\">\r\n<cross-domain-policy>\r\n   <allow-access-from domain=\"*\" to-ports=\"1-65535\" />\r\n</cross-domain-policy>\0".getBytes();
                    ChannelBuffer buffer2 = ChannelBuffers.wrappedBuffer(Bytes);
                    ChannelFuture future = Socket.write(buffer2);
                    future.addListener(ChannelFutureListener.CLOSE);
                    return;
                }
            }
            
            List<DataInputStream> Messages = new ArrayList<DataInputStream>();
            
            do
            {
                int readerindex = bufferin.readerIndex();
                byte[] byt = new byte[4];
                bufferin.readBytes(byt);
                if(Crypto != null && Crypto.RC4Decode != null)
                {
                    Crypto.RC4Decode.parse(byt);
                }
                
                // int32
                int size = ((byt[0] & 0xff) << 24) + ((byt[1] & 0xff) << 16) + ((byt[2] & 0xff) << 8) + (byt[3] & 0xff);
                
                if (size < 2 || size > 1024 * 5)
                {
                    System.out.println("Bad Size Packet!! "+size);
                    Socket.disconnect();
                    return;
                }

                if (bufferin.readableBytes() < size)
                {
                    bufferin.readerIndex(readerindex);
                    break;
                }

                byt = new byte[size];
                bufferin.readBytes(byt);
                if(Crypto != null && Crypto.RC4Decode != null)
                {
                    Crypto.RC4Decode.parse(byt);
                }
                
                Messages.add(new DataInputStream(new ByteArrayInputStream(byt)));
            }
            while(bufferin.readableBytes() >= 6);
            
            for(DataInputStream i : Messages)
            {
                int header = i.readShort();

                System.out.println("Packet-Recv: "+header);

                try
                {                        
                    if(Environment.CallBacks[header]!=null)
                    {
                        in = i;
                        Environment.CallBacks[header].ParseIn(this, Environment);
                    }
                    else
                    {
                        System.err.println("Packet desconosido <" + header + ">");
                    }

                }
                catch(Exception ex)
                {
                    Environment.Log.Print(ex);
                }
                
                i.close();
            }
        }
        catch (Exception ex)
        {
            Environment.Log.Print(ex);
        }
    }

    public void SendNotif(String Text, int... MsgType)
    {
        ServerMessage Message = new ServerMessage();
        
        if(MsgType.length != 1) // Normal
        {
            Environment.InitPacket(161, Message);
        }
        else
        {
            if(MsgType[0] == 1) // From Hotel Manager
            {
                Environment.InitPacket(139, Message);
            }
            else // MOTD
            {
                Environment.InitPacket(810, Message);
                Environment.Append(1, Message);
            }
        }

        Environment.Append(Text, Message);

        Environment.EndPacket(Socket, Message);
    }
    
    public String DecodeString()
    {
        try {
            return in.readUTF().replace('\001', ' ').replace('\002', ' '); // fix security bug
        } catch (Exception ex) {
            
        }
        return "";
    }

    public boolean DecodeCheckBox()
    {
        try {
            return (in.read() == 65);
        } catch (Exception ex) {
            
        }
        return false;
    }

    public int DecodeInt()
    {
        try {
            return in.readInt();
        } catch (Exception ex) {
            
        }
        return 0;
    }

    public void Disconnect()
    {
        Data.SetFlag(Server.plrOnline,false);
    }

    public void AddItem(int Id, String ExtraData, Item BaseItem, boolean... NeedUpdate)
    {
        UserItem UserItem = new UserItem();
        UserItem.Id = Id;
        UserItem.ExtraData = ExtraData;
        UserItem.BaseItem = BaseItem;

        if(BaseItem.Type.equals("i"))
        {
            Data.InventoryItemsWall.put(Id, UserItem);
        }
        else
        {
            Data.InventoryItems.put(Id, UserItem);
            if(UserItem.BaseItem.ItemName.startsWith("SONG"))
            {
                Data.SongInInventory.put(Id, Integer.parseInt(ExtraData));
            }
        }

        if(NeedUpdate.length == 1)
        {
            UserItem.DBNeedUpdate = true;
        }
    }

    public void InventoryRemoveItem(int Id, boolean isWall)
    {
        Environment.InitPacket(99,ClientMessage);
        Environment.Append(Id,ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);

        if(isWall)
        {
            UserItem Item = GetWallItem(Id);
            Data.InventoryItemsWall.remove(Item.Id);
        }
        else
        {
            UserItem Item = GetFloorItem(Id);
            if(Item.BaseItem.ItemName.startsWith("SONG"))
            {
                Data.SongInInventory.remove(Id);
            }
            Data.InventoryItems.remove(Item.Id);
        }
    }

    public UserItem GetWallItem(int Id)
    {
        if(Data.InventoryItemsWall.containsKey(Id))
        {
            return Data.InventoryItemsWall.get(Id);
        }
        return null;
    }

    public UserItem GetFloorItem(int Id)
    {
        if(Data.InventoryItems.containsKey(Id))
        {
            return Data.InventoryItems.get(Id);
        }
        return null;
    }

    public UserItem GetItem(int Id)
    {
        if(Data.InventoryItems.containsKey(Id))
        {
            return Data.InventoryItems.get(Id);
        }
        if(Data.InventoryItemsWall.containsKey(Id))
        {
            return Data.InventoryItemsWall.get(Id);
        }
        return null;
    }

    public Pet GetPet(int Id)
    {
        if(Data.InventoryPets.containsKey(Id))
        {
            return Data.InventoryPets.get(Id);
        }
        return null;
    }

    public void LoadRoom(int RoomId, String Password)
    {
        Room Room = Environment.RoomManager.GetRoom(RoomId);
        if (Room == null)
        {
            try
            {
                Room = Environment.RoomManager.LoadRoom(RoomId);
            }
            catch (Exception ex)
            {
                Environment.Log.Print(ex);
                return;
            }
            if (Room == null) return;
        }

        if (Room.UserIsBanned(Data.Id))
        {
            if (!Room.HasBanExpired(Data.Id))
            {
                Environment.InitPacket(224, ClientMessage);
                Environment.Append(4, ClientMessage);
                Environment.EndPacket(Socket, ClientMessage);

                Environment.InitPacket(18, ClientMessage);
                Environment.EndPacket(Socket, ClientMessage);
                return;
            }
        }

        if (Room.UsersNow >= Room.UsersMax)
        {
            Environment.InitPacket(224, ClientMessage);
            Environment.Append(1, ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);

            Environment.InitPacket(18, ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);
            return;
        }

        if (!Room.CheckRights(Data, true) && (Data.Flags & Server.plrTeleporting) != Server.plrTeleporting)
        {
            if (Room.State == 1)
            {
                if (Room.UsersNow == 0)
                {
                    Environment.InitPacket(131, ClientMessage);
                    Environment.EndPacket(Socket, ClientMessage);
                }
                else
                {
                    Environment.InitPacket(91, ClientMessage);
                    Environment.Append("", ClientMessage);
                    Environment.EndPacket(Socket, ClientMessage);
                    
                    ServerMessage Message = new ServerMessage();
                    Environment.InitPacket(91, Message);
                    Environment.Append(Data.UserName, Message);
                    Room.SendMessage(Message, true);
                }
                return;
            }
            else if (Room.State == 2)
            {
                if (!Password.equals(Room.Password))
                {
                    Environment.InitPacket(33, ClientMessage);
                    Environment.Append(-100002, ClientMessage);
                    Environment.EndPacket(Socket, ClientMessage);

                    Environment.InitPacket(18, ClientMessage);
                    Environment.EndPacket(Socket, ClientMessage);
                    return;
                }
            }
        }

        // Close Inventory
        Environment.InitPacket(19, ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);

        Environment.InitPacket(166, ClientMessage);
        Environment.Append("/client/private/" + Room.Id + "/id", ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);

        if (Data.CurrentRoom != 0)
        {
            Room OldRoom = Environment.RoomManager.GetRoom(Data.CurrentRoom);

            if (OldRoom != null)
            {
                OldRoom.RemoveUserFromRoom(this, false, false, (Data.CurrentRoom == RoomId));
                Data.CurrentRoom = 0;
            }
        }

        Room.ProcessEngine();

        Environment.InitPacket(69, ClientMessage);
        Environment.Append(Room.ModelName, ClientMessage);
        Environment.Append(Room.Id, ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);

        if (!Room.Floor.equals("0.0"))
        {
            Environment.InitPacket(46, ClientMessage);
            Environment.Append("floor", ClientMessage);
            Environment.Append(Room.Floor, ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);
        }

        if (!Room.Wallpaper.equals("0.0"))
        {
            Environment.InitPacket(46, ClientMessage);
            Environment.Append("wallpaper", ClientMessage);
            Environment.Append(Room.Wallpaper, ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);
        }

        Environment.InitPacket(46, ClientMessage);
        Environment.Append("landscape", ClientMessage);
        Environment.Append(Room.Landscape, ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);

        if (Room.CheckRights(Data, false))
        {
            Environment.InitPacket(Handler.YouAreController, ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);

            if (Room.CheckRights(Data, true))
            {
                Environment.InitPacket(Handler.YouAreOwner, ClientMessage);
                Environment.EndPacket(Socket, ClientMessage);
            }
        }
        else
        {
            Environment.InitPacket(Handler.YouAreNotController, ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);
        }

        Environment.InitPacket(345, ClientMessage);
        if (Data.RatedRooms.contains(Room.Id) || Room.CheckRights(Data, true))
        {
            Environment.Append(Room.Score, ClientMessage);
        }
        else
        {
            Environment.Append(-1, ClientMessage);
        }
        Environment.EndPacket(Socket, ClientMessage);

        if(Room.Event != null)
        {
            Environment.InitPacket(370, ClientMessage);
            Environment.Append(Integer.toString(Data.Id), ClientMessage);
            Environment.Append(Data.UserName, ClientMessage);
            Environment.Append(Integer.toString(Room.Id), ClientMessage);
            Environment.Append(Room.Event.Category, ClientMessage);
            Environment.Append(Room.Event.Name, ClientMessage);
            Environment.Append(Room.Event.Description, ClientMessage);
            Environment.Append(Room.Event.StartTime, ClientMessage);
            Environment.Append(Room.Event.Tags.size(), ClientMessage);
            for(String Tag : Room.Event.Tags)
            {
                Environment.Append(Tag, ClientMessage);
            }
            Environment.EndPacket(Socket, ClientMessage);
        }
        else
        {
            Environment.InitPacket(370, ClientMessage);
            Environment.Append("-1", ClientMessage);
            Environment.EndPacket(Socket, ClientMessage);
        }

        Data.LoadingRoom = Room.Id;
    }

    private int NextPixelsUpdate;

    public void GivePixels(int i)
    {
        ServerMessage PixelsMessage = new ServerMessage();
        Environment.InitPacket(438, PixelsMessage);
        Environment.Append(Data.Pixels += i, PixelsMessage);
        Environment.Append(i, PixelsMessage);
        Environment.Append(0, PixelsMessage); // Id Pixels
        Environment.EndPacket(Socket, PixelsMessage);
        NextPixelsUpdate = Environment.GetTimestamp() + (10 * 60); // in 10 minutes
    }


    public boolean CheckSubscription()
    {
        if(Data.Subscription.TimeExpire < Environment.GetTimestamp())
        {
            Data.Subscription.Type = 0;
            Data.Subscription.TimeExpire = 0;
            Data.MaxRooms = 5;
            return false;
        }
        return true;
    }

    public Badge GetBadge(String Badge)
    {
        for(Badge badge : Data.Badges)
        {
            if (Badge.equals(badge.Code))
            {
                return badge;
            }
        }
        return null;
    }

    public void GiveBadge(String Badge)
    {
        if(GetBadge(Badge) != null) // already have
        {
            return;
        }

        Badge badge = new Badge(Badge, 0);

        Data.Badges.add(badge);

        // UnseenItems
        ServerMessage Message = new ServerMessage();
        Environment.InitPacket(832, Message);
        Environment.Append(1, Message); // Count
        Environment.Append(4, Message); // Type
        Environment.Append(1, Message); // Ammount
        Environment.Append(badge.Slot, Message); // ItemId
        Environment.EndPacket(Socket, Message); // ItemId
    }

    private boolean HasEffect(int EffectId, boolean IfEnabledOnly)
    {
        if (EffectId == -1)
        {
            return true;
        }

        if (IfEnabledOnly)
        {
            for(AvatarEffect Effect : Data.Effects)
            {
                if (Effect.EffectId == EffectId && Effect.Activated && (Environment.GetTimestamp() - Effect.StampActivated) < Effect.TotalDuration)
                {
                    return true;
                }
            }
        }
        else
        {
            for(AvatarEffect Effect : Data.Effects)
            {
                if (Effect.EffectId == EffectId && (Environment.GetTimestamp() - Effect.StampActivated) < Effect.TotalDuration)
                {
                    return true;
                }
            }
        }

        return false;
    }


    private AvatarEffect GetEffect(int EffectId, boolean IfEnabledOnly)
    {
        if (IfEnabledOnly)
        {
            for(AvatarEffect Effect : Data.Effects)
            {
                if (Effect.EffectId == EffectId && Effect.Activated && (Environment.GetTimestamp() - Effect.StampActivated) < Effect.TotalDuration)
                {
                    return Effect;
                }
            }
        }
        else
        {
            for(AvatarEffect Effect : Data.Effects)
            {
                if (Effect.EffectId == EffectId)
                {
                    return Effect;
                }
            }
        }

        return null;
    }

    public void ApplyEffect(Room Room, int EffectId)
    {
        if (!HasEffect(EffectId, true))
        {
            return;
        }

        if(EffectId > 0)
        {
            Data.RoomUser.EffectStatus = 1;
            Data.RoomUser.IsBuyEffect = true;
            Data.RoomUser.CurrentEffect = EffectId;
        }
        else
        {
            Data.RoomUser.EffectStatus = 0;
            Data.RoomUser.IsBuyEffect = false;
        }
    }

    public void EnableEffect(int EffectId)
    {
        AvatarEffect Effect = GetEffect(EffectId, false);
        if(Effect == null)
        {
            return;
        }
        if (Effect.Activated)
        {
            return;
        }

        Effect.Activated = true;
        Effect.StampActivated = Environment.GetTimestamp();

        Environment.InitPacket(462, ClientMessage);
        Environment.Append(Effect.EffectId, ClientMessage);
        Environment.Append(Effect.TotalDuration, ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);
    }

    private void StopEffect(AvatarEffect Effect)
    {
        Data.Effects.remove(Effect);

        Environment.InitPacket(463, ClientMessage);
        Environment.Append(Effect.EffectId, ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);

        if (Data.RoomUser.CurrentEffect >= 0)
        {
            Room Room = Environment.RoomManager.GetRoom(Data.CurrentRoom);

            if (Room == null)
            {
                return;
            }

            ApplyEffect(Room, -1);
        }
    }

    public void EffectsCheckExpired()
    {
        for(AvatarEffect Effect : Data.Effects)
        {
            if (!Effect.Activated)
            {
                continue;
            }

            int diff = Environment.GetTimestamp() - Effect.StampActivated;

            if (diff > Effect.TotalDuration)
            {
                StopEffect(Effect);
            }
        }
    }

    public void AddEffect(int EffectId, int Duration)
    {
        Data.Effects.add(new AvatarEffect(EffectId, Duration, false, 0));

        Environment.InitPacket(461, ClientMessage);
        Environment.Append(EffectId, ClientMessage);
        Environment.Append(Duration, ClientMessage);
        Environment.EndPacket(Socket, ClientMessage);
    }

    public boolean PixelsNeedsUpdate()
    {
        if (Environment.GetTimestamp() > NextPixelsUpdate)
        {
            return true;
        }
        return false;
    }

    public void OnEnterRoom(int RoomId)
    {
        Data.CurrentRoom = RoomId;

        for(int FriendId : Data.Friends)
        {
            Player pClient = Environment.ClientManager.GetClient(FriendId);
            if(pClient == null) continue;

            if((pClient.Flags & Server.plrOnline) == Server.plrOnline) // Is Online?
            {
                pClient.FriendsUpdateNeeded.add(Data.Id);
            }
        }
    }

    public void OnLeaveRoom()
    {
        Data.CurrentRoom = 0;

        for(int FriendId : Data.Friends)
        {
            Player pClient = Environment.ClientManager.GetClient(FriendId);
            if(pClient == null) continue;

            if((pClient.Flags & Server.plrOnline) == Server.plrOnline) // Is Online?
            {
                pClient.FriendsUpdateNeeded.add(Data.Id);
            }
        }
    }
}
