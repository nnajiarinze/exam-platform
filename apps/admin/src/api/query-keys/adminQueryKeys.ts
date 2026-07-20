export const adminQueryKeys={
 exams:{all:['exams'] as const,list:(filters:unknown)=>['exams','list',filters] as const,detail:(id:string)=>['exams','detail',id] as const,versions:(id:string)=>['exams',id,'versions'] as const},
 structure:{subjects:(id:string)=>['subjects',id] as const,topics:(id:string)=>['topics',id] as const},
 sources:{all:['sources'] as const,list:(filters:unknown)=>['sources','list',filters] as const,detail:(id:string)=>['sources','detail',id] as const},
 objectives:{all:['learning-objectives'] as const,list:(filters:unknown)=>['learning-objectives','list',filters] as const,detail:(id:string)=>['learning-objectives','detail',id] as const},
 facts:{all:['knowledge-facts'] as const,list:(filters:unknown)=>['knowledge-facts','list',filters] as const,detail:(id:string)=>['knowledge-facts','detail',id] as const,versions:(id:string)=>['knowledge-facts',id,'versions'] as const}
};
